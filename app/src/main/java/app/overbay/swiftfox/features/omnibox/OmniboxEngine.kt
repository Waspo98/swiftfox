package app.overbay.swiftfox.features.omnibox

import app.overbay.swiftfox.BuildConfig
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.SearchEngineId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
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

data class InlineUrlAutocomplete(
    val typedPrefix: String,
    val completionSuffix: String,
    val displayText: String,
    val targetUrl: String
)

data class AutocompleteCandidate(
    val url: String,
    val priority: Int = 0,
    val visitCount: Int = 0,
    val lastVisitedAt: Long = 0L
)

data class NavigationalSuggestion(
    val url: String,
    val displayHost: String,
    val title: String,
    val relevance: Int = 0
)

data class LiveSuggestionsResult(
    val navigationalHits: List<NavigationalSuggestion> = emptyList(),
    val searchQueries: List<String> = emptyList()
)

/**
 * Pure state machine controlling when inline URL autocomplete is suppressed in the omnibox
 * (prefilled URL open, active deletion, or explicit Backspace dismissal of a suggested suffix).
 */
class OmniboxAutocompleteGate(initialSuppressed: Boolean = false) {
    var isSuppressed: Boolean = initialSuppressed
        private set
    var dismissedPrefix: String? = null
        private set

    fun onOverlayOpened(prefilledQuery: String) {
        isSuppressed = prefilledQuery.isNotEmpty()
        dismissedPrefix = null
    }

    fun onExternalQuerySynced(query: String) {
        isSuppressed = query.isEmpty()
        dismissedPrefix = null
    }

    fun onCleared() {
        isSuppressed = false
        dismissedPrefix = null
    }

    /**
     * Intercepts a single trailing-character Backspace while an inline completion is highlighted so
     * the first Backspace dismisses the completion suffix instead of deleting the user's typed
     * character (matching Chrome/Firefox omnibox behavior).
     */
    fun interceptBackspaceToDismiss(
        originalText: CharSequence,
        proposedText: CharSequence,
        proposedSelectionCollapsedAtEnd: Boolean,
        hasActiveAutocomplete: Boolean
    ): Boolean {
        if (!hasActiveAutocomplete || !proposedSelectionCollapsedAtEnd) return false
        val orig = originalText.toString()
        val next = proposedText.toString()
        if (orig.isNotEmpty() && next.length == orig.length - 1 && orig.startsWith(next)) {
            isSuppressed = true
            dismissedPrefix = orig
            return true
        }
        return false
    }

    fun onUserTextCommitted(previousText: String, newText: String) {
        if (newText.isEmpty()) {
            isSuppressed = true
            dismissedPrefix = null
            return
        }
        val isDeletion = newText.length < previousText.length &&
            previousText.startsWith(newText, ignoreCase = true)
        val dismissed = dismissedPrefix
        if (dismissed != null) {
            if (isDeletion || newText.equals(dismissed, ignoreCase = true)) {
                isSuppressed = true
                return
            }
            dismissedPrefix = null
        }
        isSuppressed = isDeletion
    }
}

object OmniboxEngine {

    const val NEW_TAB_URL = "about:blank"

    const val PRIORITY_OPEN_TAB = 40
    const val PRIORITY_ESSENTIAL_OR_PIN = 30
    const val PRIORITY_UNLOADED_TAB = 25
    const val PRIORITY_BOOKMARK = 20
    const val PRIORITY_REMOTE_TAB = 15
    const val PRIORITY_HISTORY = 10
    const val PRIORITY_NAV_SUGGESTION = 6
    const val PRIORITY_TOP_DOMAIN = 5

    private const val MIN_SINGLE_CHAR_HISTORY_VISITS = 3

    val quickTldShortcuts: List<String> = listOf(
        ".com", ".org", ".net", ".io", ".app", ".ai", ".dev", ".gov", ".edu"
    )

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

    @Volatile
    private var configuredClientProvider: (() -> OkHttpClient)? = null

    private val fallbackHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    fun configureHttpClientProvider(provider: () -> OkHttpClient) {
        configuredClientProvider = provider
    }

    private val httpClient: OkHttpClient
        get() {
            configuredClient?.let { return it }
            val provider = configuredClientProvider ?: return fallbackHttpClient
            return synchronized(this) {
                configuredClient ?: provider().newBuilder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .build()
                    .also { configuredClient = it }
            }
        }

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

    fun buildSearchUrl(
        query: String,
        settings: BrowserSettings,
        engineOverride: SearchEngineId? = null
    ): String {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        return settings.resolvedSearchTemplate(engineOverride).replace("%s", encoded)
    }

    fun bangHomepageUrl(bang: SearchBang): String {
        return runCatching {
            val uri = URI(bang.urlTemplate.replace("%s", ""))
            val scheme = uri.scheme ?: "https"
            val authority = uri.rawAuthority ?: return@runCatching bang.urlTemplate.replace("%s", "")
            "$scheme://$authority/"
        }.getOrElse { bang.urlTemplate.replace("%s", "") }
    }

    fun resolveInput(
        rawInput: String,
        settings: BrowserSettings,
        searchScope: OmniboxSearchScope? = null
    ): String {
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

        if (searchScope is OmniboxSearchScope.Engine) {
            return buildSearchUrl(trimmed, settings, engineOverride = searchScope.engine)
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
            val authorityEnd = trimmed.indexOfFirst { it == '/' || it == '?' || it == '#' }
            val normalizedTarget = if (authorityEnd >= 0) {
                trimmed.substring(0, authorityEnd).lowercase(Locale.ROOT) + trimmed.substring(authorityEnd)
            } else {
                trimmed.lowercase(Locale.ROOT)
            }
            return "$defaultScheme$normalizedTarget"
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
        var start = 0
        var end = url.length
        while (start < end && url[start].isWhitespace()) start++
        while (end > start && url[end - 1].isWhitespace()) end--
        if (end <= start) return ""
        if (end - start == NEW_TAB_URL.length && url.regionMatches(start, NEW_TAB_URL, 0, NEW_TAB_URL.length, ignoreCase = true)) {
            return ""
        }
        val schemeSep = url.indexOf("://", start)
        if (schemeSep <= start || schemeSep >= end) return ""
        for (i in start until schemeSep) {
            val sc = url[i]
            val validSchemeChar = (sc in 'a'..'z') || (sc in 'A'..'Z') ||
                (i > start && ((sc in '0'..'9') || sc == '+' || sc == '.' || sc == '-'))
            if (!validSchemeChar) return ""
        }
        val authStart = schemeSep + 3
        if (authStart >= end) return ""
        var authEnd = end
        for (i in authStart until end) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#' || c == '\\') {
                authEnd = i
                break
            }
        }
        if (authEnd <= authStart) return ""
        var hostStart = authStart
        for (i in authEnd - 1 downTo authStart) {
            if (url[i] == '@') {
                hostStart = i + 1
                break
            }
        }
        if (hostStart >= authEnd) return ""
        val rawHost = if (url[hostStart] == '[') {
            val closeBracket = url.indexOf(']', hostStart + 1)
            if (closeBracket <= hostStart + 1 || closeBracket >= authEnd) return ""
            url.substring(hostStart + 1, closeBracket)
        } else {
            var colonIdx = -1
            for (i in hostStart until authEnd) {
                if (url[i] == ':') {
                    colonIdx = i
                    break
                }
            }
            val hostEnd = if (colonIdx >= 0) colonIdx else authEnd
            if (hostEnd <= hostStart) return ""
            url.substring(hostStart, hostEnd)
        }
        return normalizeHost(rawHost)
    }

    private data class ParsedCandidateUrl(
        val scheme: String,
        val authority: String,
        val hostNoWww: String,
        val hostNoMobile: String,
        val cleanPath: String
    )

    private fun stripMobileHostPrefix(hostNoWww: String): String {
        if (hostNoWww.contains(':') || hostNoWww.startsWith("[")) return hostNoWww
        val stripped = when {
            hostNoWww.startsWith("mobile.") -> hostNoWww.removePrefix("mobile.")
            hostNoWww.startsWith("m.") -> hostNoWww.removePrefix("m.")
            else -> return hostNoWww
        }
        return if (stripped.contains('.') && !stripped.startsWith(".") && !stripped.endsWith(".")) {
            stripped
        } else {
            hostNoWww
        }
    }

    private fun parseCandidateUrl(rawUrl: String): ParsedCandidateUrl? {
        val trimmed = rawUrl.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = trimmed.substring(0, schemeEnd).lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val afterScheme = trimmed.substring(schemeEnd + 3)
        if (afterScheme.isEmpty()) return null
        val pathStart = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val rawAuthority = (if (pathStart >= 0) afterScheme.substring(0, pathStart) else afterScheme)
            .substringAfterLast('@')
            .lowercase(Locale.ROOT)
        if (rawAuthority.isBlank() || rawAuthority.contains('\\') || rawAuthority.any { it.isWhitespace() }) {
            return null
        }
        val normalizedAuthority = when {
            scheme == "https" && rawAuthority.endsWith(":443") -> rawAuthority.removeSuffix(":443")
            scheme == "http" && rawAuthority.endsWith(":80") -> rawAuthority.removeSuffix(":80")
            else -> rawAuthority
        }
        val hostNoWww = normalizedAuthority.removePrefix("www.")
        if (hostNoWww.isBlank()) return null
        val hostNoMobile = stripMobileHostPrefix(hostNoWww)
        val rawPath = if (pathStart >= 0 && afterScheme[pathStart] == '/') {
            afterScheme.substring(pathStart).substringBefore('?').substringBefore('#')
        } else {
            ""
        }
        return ParsedCandidateUrl(
            scheme = scheme,
            authority = normalizedAuthority,
            hostNoWww = hostNoWww,
            hostNoMobile = hostNoMobile,
            cleanPath = rawPath
        )
    }

    private fun extractHostOnlyFromAuthority(authority: String): String {
        return if (authority.startsWith("[")) {
            val endBracket = authority.indexOf(']')
            if (endBracket > 0) authority.substring(0, endBracket + 1) else authority
        } else {
            authority.substringBefore(':')
        }
    }

    /**
     * Lowercases the `http`/`https` scheme and host portion of [url] while preserving userinfo,
     * port, path, query, and fragment case verbatim so SQLite B-tree prefix range scans and
     * bookmark URL lookups match regardless of import/sync casing.
     */
    fun canonicalizeUrlSchemeAndHost(url: String): String {
        val trimmed = url.trim()
        val schemeSep = trimmed.indexOf("://")
        if (schemeSep <= 0) return trimmed
        val scheme = trimmed.substring(0, schemeSep)
        val lowerScheme = when {
            scheme.equals("https", ignoreCase = true) -> "https"
            scheme.equals("http", ignoreCase = true) -> "http"
            else -> return trimmed
        }
        val authStart = schemeSep + 3
        if (authStart >= trimmed.length) return trimmed
        var authEnd = trimmed.length
        for (i in authStart until trimmed.length) {
            val c = trimmed[i]
            if (c == '/' || c == '?' || c == '#' || c == '\\') {
                authEnd = i
                break
            }
        }
        if (authEnd <= authStart) return trimmed
        var hostStart = authStart
        for (i in authEnd - 1 downTo authStart) {
            if (trimmed[i] == '@') {
                hostStart = i + 1
                break
            }
        }
        if (hostStart >= authEnd) return trimmed
        val hostEnd = if (trimmed[hostStart] == '[') {
            val closeBracket = trimmed.indexOf(']', hostStart + 1)
            if (closeBracket <= hostStart + 1 || closeBracket >= authEnd) return trimmed
            closeBracket + 1
        } else {
            var colonIdx = -1
            for (i in hostStart until authEnd) {
                if (trimmed[i] == ':') {
                    colonIdx = i
                    break
                }
            }
            if (colonIdx >= 0) colonIdx else authEnd
        }
        if (hostEnd <= hostStart) return trimmed

        var needsChange = scheme != lowerScheme
        if (!needsChange) {
            for (i in hostStart until hostEnd) {
                if (trimmed[i] in 'A'..'Z') {
                    needsChange = true
                    break
                }
            }
        }
        if (!needsChange) return trimmed

        return buildString(trimmed.length) {
            append(lowerScheme)
            append("://")
            if (hostStart > authStart) {
                append(trimmed, authStart, hostStart)
            }
            for (i in hostStart until hostEnd) {
                val c = trimmed[i]
                append(if (c in 'A'..'Z') (c.code + 32).toChar() else c)
            }
            if (hostEnd < trimmed.length) {
                append(trimmed, hostEnd, trimmed.length)
            }
        }
    }

    /**
     * Extracts a clean lowercase host/URL prefix from omnibox input suitable for dedicated
     * Room index-backed B-tree range prefix queries so article title matches never crowd out
     * domain completions.
     */
    fun extractRawHostPrefixForDbQuery(rawQuery: String): String? {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        if (trimmed.startsWith("!") || trimmed.startsWith("@")) return null
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower == "http:" || lower == "https:" || lower == "http:/" || lower == "https:/") return null
        val afterScheme = when {
            lower.startsWith("https://") -> lower.removePrefix("https://")
            lower.startsWith("http://") -> lower.removePrefix("http://")
            else -> lower
        }
        val normalized = when {
            afterScheme.startsWith("www.") -> afterScheme.removePrefix("www.")
            afterScheme.startsWith("mobile.") -> afterScheme.removePrefix("mobile.")
            afterScheme.startsWith("m.") -> afterScheme.removePrefix("m.")
            else -> afterScheme
        }
        if (normalized.isEmpty()) return null
        return normalized
    }

    /**
     * Returns true when the user is typing a single domain-like token without spaces or a
     * completed TLD (e.g. `theverge`, `theverge.`, `https://myblog`), so the omnibox can
     * surface one-tap `.com`/`.org`/`.net` shortcut chips.
     */
    fun shouldShowQuickTldChips(rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
        if (trimmed.startsWith("!") || trimmed.startsWith("@")) return false
        if (trimmed.startsWith("about:", ignoreCase = true)) return false
        if (trimmed.contains('?') || trimmed.contains('#')) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower == "http:" || lower == "https:" || lower == "http:/" || lower == "https:/") {
            return false
        }
        val remainder = when {
            lower.startsWith("https://") -> lower.removePrefix("https://")
            lower.startsWith("http://") -> lower.removePrefix("http://")
            else -> lower
        }
        if (remainder.isEmpty() || remainder.contains('/')) return false
        if (looksLikeUrl(remainder)) return false

        val withoutWww = remainder.removePrefix("www.")
        val hasTrailingDot = withoutWww.endsWith('.')
        val hostStub = if (hasTrailingDot) withoutWww.removeSuffix(".") else withoutWww
        if (hostStub.isEmpty() || hostStub == "www") return false
        // Reject filenames/versions (e.g. "node.js", "v1.2") unless user explicitly typed a trailing dot
        if (!hasTrailingDot && hostStub.contains('.')) return false
        if (!hostStub.any { it.isLetter() }) return false
        return hostStub.split('.').all { label ->
            label.isNotEmpty() &&
                label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    /**
     * Applies a quick TLD shortcut (`.com`, `.org`, etc.) cleanly to [rawInput],
     * avoiding duplicate dots.
     */
    fun applyTldShortcut(rawInput: String, shortcut: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || shortcut.isEmpty()) return null
        val base = trimmed.trimEnd('.')
        if (base.isEmpty()) return null
        return "$base$shortcut"
    }

    /**
     * Deduplicates autocomplete candidates within each source priority tier by trimmed URL so
     * combining in-memory state with Room/Places search results never double-counts `visitCount`.
     */
    fun dedupeAutocompleteCandidates(
        candidates: List<AutocompleteCandidate>
    ): List<AutocompleteCandidate> {
        if (candidates.size <= 1) return candidates
        val byKey = LinkedHashMap<Pair<Int, String>, AutocompleteCandidate>(candidates.size)
        for (candidate in candidates) {
            val trimmedUrl = candidate.url.trim()
            if (trimmedUrl.isEmpty()) continue
            val dedupeUrl = trimmedUrl.trimEnd('/').lowercase(Locale.ROOT)
            val key = candidate.priority to dedupeUrl
            val existing = byKey[key]
            if (existing == null) {
                byKey[key] = candidate
            } else {
                byKey[key] = existing.copy(
                    visitCount = maxOf(existing.visitCount, candidate.visitCount),
                    lastVisitedAt = maxOf(existing.lastVisitedAt, candidate.lastVisitedAt)
                )
            }
        }
        return byKey.values.toList()
    }

    private fun isCandidateEligibleForPrefix(
        candidate: AutocompleteCandidate,
        effectiveHostPrefix: String,
        typedHasPath: Boolean
    ): Boolean {
        if (typedHasPath) {
            return candidate.priority >= PRIORITY_HISTORY
        }
        val len = effectiveHostPrefix.length
        return when {
            len <= 0 -> false
            len == 1 -> candidate.priority >= PRIORITY_BOOKMARK ||
                (candidate.priority >= PRIORITY_HISTORY && candidate.visitCount >= MIN_SINGLE_CHAR_HISTORY_VISITS)
            len == 2 -> candidate.priority >= PRIORITY_HISTORY ||
                (candidate.priority >= PRIORITY_TOP_DOMAIN && effectiveHostPrefix.contains('.'))
            else -> true
        }
    }

    /**
     * Finds the best inline URL completion for [rawQuery] across open tabs, pinned items,
     * bookmarks, synced remote tabs, local & synced history, navigational search suggestions,
     * and offline top domains so pressing Enter navigates directly to the highlighted site.
     */
    fun findInlineAutocomplete(
        rawQuery: String,
        candidates: List<AutocompleteCandidate>
    ): InlineUrlAutocomplete? {
        if (rawQuery.isEmpty() || candidates.isEmpty()) return null
        if (rawQuery.any { it.isWhitespace() }) return null
        if (rawQuery.startsWith("!") || rawQuery.startsWith("@")) return null
        if (rawQuery.contains('?') || rawQuery.contains('#')) return null

        val typedLower = rawQuery.lowercase(Locale.ROOT)
        if (typedLower == "http:" || typedLower == "https:" || typedLower == "http:/" || typedLower == "https:/") {
            return null
        }

        val typedSchemePrefix: String? = when {
            typedLower.startsWith("https://") -> "https://"
            typedLower.startsWith("http://") -> "http://"
            else -> null
        }
        val typedRemainder = if (typedSchemePrefix != null) {
            typedLower.removePrefix(typedSchemePrefix)
        } else {
            typedLower
        }
        if (typedRemainder.isEmpty()) return null

        val typedHasWww = typedRemainder.startsWith("www.")
        val typedHasMobilePrefix = typedRemainder.startsWith("m.") || typedRemainder.startsWith("mobile.")
        val typedHasPath = typedRemainder.contains('/')
        val effectiveHostPrefix = when {
            typedHasWww -> typedRemainder.removePrefix("www.")
            else -> typedRemainder
        }.substringBefore('/')
        if (effectiveHostPrefix.isEmpty()) return null
        val displaySchemePrefix = typedSchemePrefix.orEmpty()

        data class ScoredMatch(
            val displayKey: String,
            var suffix: String,
            var targetUrl: String,
            var maxPriority: Int,
            var personalVisits: Int,
            var fallbackScore: Int,
            var latestVisit: Long,
            var preferHttps: Boolean
        )

        val grouped = LinkedHashMap<String, ScoredMatch>()
        val dedupedCandidates = dedupeAutocompleteCandidates(candidates)

        for (candidate in dedupedCandidates) {
            if (!isCandidateEligibleForPrefix(candidate, effectiveHostPrefix, typedHasPath)) {
                continue
            }
            val parsed = parseCandidateUrl(candidate.url) ?: continue
            val bareHost = extractHostOnlyFromAuthority(parsed.hostNoWww)
            val effectiveScheme = when (typedSchemePrefix) {
                "http://" -> if (parsed.scheme == "http") "http" else continue
                "https://" -> if (parsed.scheme == "https" || !isLocalHostOrLan(bareHost)) {
                    "https"
                } else {
                    continue
                }
                else -> parsed.scheme
            }

            val displayHost: String
            val targetAuthority: String
            when {
                typedHasWww -> {
                    if (parsed.authority.startsWith("www.")) {
                        displayHost = parsed.authority
                        targetAuthority = parsed.authority
                    } else if (!isLocalHostOrLan(bareHost) &&
                        !parsed.authority.contains(':') &&
                        parsed.hostNoWww.count { it == '.' } == 1
                    ) {
                        displayHost = "www.${parsed.hostNoWww}"
                        targetAuthority = parsed.authority
                    } else {
                        displayHost = parsed.authority
                        targetAuthority = parsed.authority
                    }
                }
                typedHasMobilePrefix -> {
                    displayHost = parsed.hostNoWww
                    targetAuthority = parsed.authority
                }
                else -> {
                    displayHost = parsed.hostNoMobile
                    targetAuthority = if (parsed.hostNoMobile != parsed.hostNoWww) {
                        parsed.hostNoMobile
                    } else {
                        parsed.authority
                    }
                }
            }

            val completedDisplay: String
            val targetUrl: String
            if (!typedHasPath) {
                completedDisplay = "$displaySchemePrefix$displayHost"
                if (!completedDisplay.startsWith(typedLower) || completedDisplay.length <= typedLower.length) {
                    continue
                }
                targetUrl = "$effectiveScheme://$targetAuthority/"
            } else {
                if (parsed.cleanPath.isEmpty()) continue
                val fullDisplay = "$displaySchemePrefix$displayHost${parsed.cleanPath}"
                val fullDisplayLower = fullDisplay.lowercase(Locale.ROOT)
                if (!fullDisplayLower.startsWith(typedLower) || fullDisplay.length <= typedLower.length) {
                    continue
                }
                val nextSlash = fullDisplay.indexOf('/', startIndex = typedLower.length)
                completedDisplay = if (nextSlash >= 0) {
                    fullDisplay.substring(0, nextSlash + 1)
                } else {
                    fullDisplay
                }
                val completedPath = completedDisplay.removePrefix("$displaySchemePrefix$displayHost")
                targetUrl = "$effectiveScheme://$targetAuthority$completedPath"
            }

            if (completedDisplay.length <= typedLower.length) continue
            val suffix = completedDisplay.substring(typedLower.length)
            if (suffix.isEmpty()) continue

            val key = completedDisplay.lowercase(Locale.ROOT)
            val existing = grouped[key]
            val isPersonal = candidate.priority >= PRIORITY_HISTORY
            val weight = candidate.visitCount.coerceAtLeast(1)
            val isHttps = effectiveScheme == "https"
            if (existing == null) {
                grouped[key] = ScoredMatch(
                    displayKey = key,
                    suffix = suffix,
                    targetUrl = targetUrl,
                    maxPriority = candidate.priority,
                    personalVisits = if (isPersonal) weight else 0,
                    fallbackScore = if (isPersonal) 0 else weight,
                    latestVisit = if (isPersonal) candidate.lastVisitedAt else 0L,
                    preferHttps = isHttps
                )
            } else {
                val higherPriority = candidate.priority > existing.maxPriority
                val upgradeToHttps = isHttps && !existing.preferHttps && candidate.priority >= existing.maxPriority
                if (higherPriority) {
                    existing.maxPriority = candidate.priority
                }
                if (higherPriority || upgradeToHttps) {
                    existing.targetUrl = targetUrl
                    existing.suffix = suffix
                    existing.preferHttps = isHttps
                }
                if (isPersonal) {
                    existing.personalVisits += weight
                    if (candidate.lastVisitedAt > existing.latestVisit) {
                        existing.latestVisit = candidate.lastVisitedAt
                    }
                } else if (weight > existing.fallbackScore) {
                    existing.fallbackScore = weight
                }
            }
        }

        val best = grouped.values.maxWithOrNull(
            compareBy<ScoredMatch> { it.maxPriority }
                .thenBy { it.personalVisits }
                .thenBy { it.latestVisit }
                .thenBy { it.fallbackScore }
                .thenByDescending { it.displayKey.length }
        ) ?: return null

        return InlineUrlAutocomplete(
            typedPrefix = rawQuery,
            completionSuffix = best.suffix,
            displayText = rawQuery + best.suffix,
            targetUrl = best.targetUrl
        )
    }

    private fun parseStrictIpv4Octets(host: String): IntArray? {
        if (host.isEmpty() || host[0] !in '0'..'9') return null
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

    /**
     * Parses OpenSearch JSON arrays (`[query, [suggestions...]]`) and Google `client=chrome`
     * payloads (which include `google:suggesttype` = `"NAVIGATION"` and `google:suggestrelevance`).
     */
    fun parseLiveSuggestionsResponse(body: String): LiveSuggestionsResult {
        if (body.isBlank()) return LiveSuggestionsResult()
        return runCatching {
            val root = json.parseToJsonElement(body).jsonArray
            if (root.size < 2 || root[1] !is JsonArray) return LiveSuggestionsResult()
            val items = root[1].jsonArray
            val descriptions = root.getOrNull(2) as? JsonArray
            val metadata = root.getOrNull(4) as? JsonObject
            val suggestTypes = metadata?.get("google:suggesttype") as? JsonArray
            val relevances = metadata?.get("google:suggestrelevance") as? JsonArray

            val navHits = mutableListOf<NavigationalSuggestion>()
            val seenNavUrls = HashSet<String>()
            val queries = mutableListOf<String>()
            val seenQueries = HashSet<String>()

            for (i in items.indices) {
                val rawText = (items[i] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (rawText.isEmpty()) continue
                val type = (suggestTypes?.getOrNull(i) as? JsonPrimitive)?.contentOrNull
                if (type.equals("NAVIGATION", ignoreCase = true)) {
                    val parsed = parseCandidateUrl(rawText) ?: continue
                    val bareHost = extractHostOnlyFromAuthority(parsed.hostNoWww)
                    if (isLocalHostOrLan(bareHost)) continue
                    val effectiveScheme = if (parsed.scheme == "http" && !parsed.authority.contains(':')) {
                        "https"
                    } else {
                        parsed.scheme
                    }
                    val path = parsed.cleanPath.ifEmpty { "/" }
                    val normalizedUrl = "$effectiveScheme://${parsed.authority}$path"
                    if (seenNavUrls.add(normalizedUrl.lowercase(Locale.ROOT))) {
                        val desc = (descriptions?.getOrNull(i) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                        val relevance = (relevances?.getOrNull(i) as? JsonPrimitive)?.intOrNull ?: 0
                        navHits += NavigationalSuggestion(
                            url = normalizedUrl,
                            displayHost = parsed.hostNoWww,
                            title = desc.ifBlank { parsed.hostNoWww },
                            relevance = relevance
                        )
                    }
                } else if (queries.size < 5) {
                    if (seenQueries.add(rawText.lowercase(Locale.ROOT))) {
                        queries += rawText
                    }
                }
            }

            LiveSuggestionsResult(
                navigationalHits = navHits
                    .sortedByDescending { it.relevance }
                    .take(2),
                searchQueries = queries
            )
        }.getOrDefault(LiveSuggestionsResult())
    }

    suspend fun fetchLiveSuggestionsResult(
        query: String,
        settings: BrowserSettings,
        engineOverride: SearchEngineId? = null
    ): LiveSuggestionsResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2 || !settings.searchSuggestionsEnabled) return@withContext LiveSuggestionsResult()
        if (detectBang(trimmed) != null) return@withContext LiveSuggestionsResult()
        if (engineOverride == null && looksLikeUrl(trimmed)) return@withContext LiveSuggestionsResult()

        val activeEngine = engineOverride ?: settings.searchEngine
        val template = activeEngine.suggestUrlTemplate ?: return@withContext LiveSuggestionsResult()
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
                if (!response.isSuccessful) return@use LiveSuggestionsResult()
                val body = readBoundedBody(response.body, MAX_SUGGESTION_BYTES)
                parseLiveSuggestionsResponse(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            LiveSuggestionsResult()
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
