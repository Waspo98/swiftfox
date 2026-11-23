package app.overbay.swiftfox.features.userscripts

import java.util.Collections

/**
 * Evaluates `@match`, `@include`, `@exclude`, `@exclude-match`, `<all_urls>`, and `/regex/`
 * patterns against HTTP/HTTPS URLs with bounded pattern caching.
 */
object UserscriptUrlMatcher {

    private const val MAX_CACHED_PATTERNS = 256

    private val globRegexCache: MutableMap<String, Regex> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Regex>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex>?): Boolean =
                size > MAX_CACHED_PATTERNS
        }
    )

    private val userRegexCache: MutableMap<String, Regex?> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Regex?>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex?>?): Boolean =
                size > MAX_CACHED_PATTERNS
        }
    )

    private data class UrlParts(
        val scheme: String,
        val host: String,
        val port: String?,
        val pathAndQuery: String,
        val urlWithoutFragment: String
    )

    private data class MatchPattern(
        val scheme: String,
        val host: String,
        val port: String?,
        val pathPattern: String
    )

    data class JsUrlCheck(
        val regexBody: String,
        val flags: String,
        val matchFullHref: Boolean = false
    )

    fun isHttpOrHttps(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }

    fun matches(
        url: String,
        matches: List<String>,
        excludes: List<String>
    ): Boolean {
        val trimmedUrl = url.trim()
        if (!isHttpOrHttps(trimmedUrl)) return false
        if (matches.isEmpty()) return false
        if (excludes.any { patternMatches(it, trimmedUrl) }) return false
        return matches.any { patternMatches(it, trimmedUrl) }
    }

    fun compileJsCheck(pattern: String): JsUrlCheck? {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "<all_urls>") {
            return JsUrlCheck(regexBody = """^https?://""", flags = "i")
        }
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            val parsed = parseRegexComponents(trimmed) ?: return null
            val supportedFlags = parsed.second.filter { it in charArrayOf('i', 'm', 's', 'u') }
            return JsUrlCheck(regexBody = parsed.first, flags = supportedFlags, matchFullHref = true)
        }
        val strict = parseStrictMatchPattern(trimmed)
        if (strict != null) {
            val schemeExpr = when (strict.scheme) {
                "*" -> "https?"
                else -> escapeJsRegexLiteral(strict.scheme)
            }
            val hostExpr = when {
                strict.host == "*" -> """[^/?#]+"""
                strict.host.startsWith("*.") -> {
                    val base = escapeJsRegexLiteral(strict.host.removePrefix("*."))
                    """(?:[^/?#]+\.)?$base"""
                }
                else -> escapeJsRegexLiteral(strict.host)
            }
            val portExpr = when {
                strict.port == null -> """(?::\d+)?"""
                strict.port == "*" -> """(?::\d+)?"""
                else -> {
                    val escapedPort = escapeJsRegexLiteral(strict.port)
                    val isDefaultPort = (strict.scheme == "http" && strict.port == "80") ||
                        (strict.scheme == "https" && strict.port == "443") ||
                        (strict.scheme == "*" && (strict.port == "80" || strict.port == "443"))
                    if (isDefaultPort) """(?::$escapedPort)?""" else ":$escapedPort"
                }
            }
            val pathExpr = globToJsRegexBody(collapseRepeatedStars(strict.pathPattern), starReplacement = ".*")
            return JsUrlCheck(
                regexBody = "^$schemeExpr://$hostExpr$portExpr$pathExpr$",
                flags = "i"
            )
        }

        val schemeSep = trimmed.indexOf("://")
        if (schemeSep > 0) {
            val patScheme = trimmed.substring(0, schemeSep)
            val patRest = trimmed.substring(schemeSep + 3)
            val slashIdx = patRest.indexOf('/')
            val schemeRegex = globToJsRegexBody(collapseRepeatedStars(patScheme), starReplacement = "[^:/]*")
            return if (slashIdx >= 0) {
                val patAuthority = patRest.substring(0, slashIdx)
                val patPath = patRest.substring(slashIdx)
                val authRegex = globToJsRegexBody(collapseRepeatedStars(patAuthority), starReplacement = "[^/]*")
                val pathRegex = globToJsRegexBody(collapseRepeatedStars(patPath), starReplacement = ".*")
                JsUrlCheck(regexBody = "^$schemeRegex://$authRegex$pathRegex$", flags = "i")
            } else if (patRest == "*") {
                JsUrlCheck(regexBody = "^$schemeRegex://.*$", flags = "i")
            } else if (patRest.endsWith("*")) {
                val prefix = patRest.removeSuffix("*")
                val prefixRegex = globToJsRegexBody(collapseRepeatedStars(prefix), starReplacement = "[^/]*")
                JsUrlCheck(regexBody = "^$schemeRegex://$prefixRegex.*$", flags = "i")
            } else {
                val body = globToJsRegexBody(collapseRepeatedStars(patRest), starReplacement = "[^/]*")
                JsUrlCheck(regexBody = "^$schemeRegex://$body/?$", flags = "i")
            }
        }

        val collapsed = collapseRepeatedStars(trimmed)
        return JsUrlCheck(
            regexBody = "^${globToJsRegexBody(collapsed, starReplacement = ".*")}$",
            flags = "i"
        )
    }

    fun patternMatches(pattern: String, url: String): Boolean {
        val trimmedPattern = pattern.trim()
        val trimmedUrl = url.trim()
        if (trimmedPattern.isEmpty() || !isHttpOrHttps(trimmedUrl)) return false

        val urlParts = parseUrlParts(trimmedUrl) ?: return false

        if (trimmedPattern == "<all_urls>") {
            return true
        }

        if (trimmedPattern.startsWith("/") && !trimmedPattern.startsWith("//")) {
            val regex = cachedUserRegex(trimmedPattern)
            if (regex != null) {
                return regex.containsMatchIn(trimmedUrl)
            }
        }

        val matchPattern = parseStrictMatchPattern(trimmedPattern)
        if (matchPattern != null) {
            return matchesStrictPattern(matchPattern, urlParts)
        }

        val schemeSep = trimmedPattern.indexOf("://")
        if (schemeSep > 0) {
            return matchesSplitGlobUrl(trimmedPattern, schemeSep, urlParts)
        }

        if (wildcardGlobMatches(trimmedPattern, urlParts.urlWithoutFragment)) {
            return true
        }
        // Support trailing-slash-less @include patterns like "https://example.com" matching "https://example.com/"
        if (!trimmedPattern.endsWith("/") &&
            !trimmedPattern.endsWith("*") &&
            urlParts.urlWithoutFragment.endsWith("/") &&
            wildcardGlobMatches("$trimmedPattern/", urlParts.urlWithoutFragment)
        ) {
            return true
        }
        return false
    }

    private fun parseUrlParts(url: String): UrlParts? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase(java.util.Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null

        val hashIdx = url.indexOf('#', startIndex = schemeEnd + 3)
        val urlWithoutFragment = if (hashIdx >= 0) url.substring(0, hashIdx) else url
        val afterScheme = urlWithoutFragment.substring(schemeEnd + 3)
        val authorityEnd = listOf(
            afterScheme.indexOf('/'),
            afterScheme.indexOf('?')
        ).filter { it >= 0 }.minOrNull() ?: afterScheme.length

        val rawAuthority = afterScheme.substring(0, authorityEnd)
        val authority = rawAuthority.substringAfterLast('@')
        if (authority.isEmpty()) return null

        val (host, port) = splitHostAndPort(authority) ?: return null
        if (host.isEmpty()) return null

        val remainder = afterScheme.substring(authorityEnd)
        val pathAndQuery = when {
            remainder.isEmpty() -> "/"
            remainder.startsWith("/") -> remainder
            else -> "/$remainder"
        }
        return UrlParts(
            scheme = scheme,
            host = host,
            port = port,
            pathAndQuery = pathAndQuery,
            urlWithoutFragment = urlWithoutFragment
        )
    }

    private fun splitHostAndPort(authority: String): Pair<String, String?>? {
        if (authority.startsWith("[")) {
            val closeIdx = authority.indexOf(']')
            if (closeIdx < 0) return null
            val host = authority.substring(0, closeIdx + 1).lowercase(java.util.Locale.ROOT)
            val rest = authority.substring(closeIdx + 1)
            val port = if (rest.startsWith(":")) rest.substring(1).ifEmpty { null } else if (rest.isEmpty()) null else return null
            return host to port
        }
        val colonIdx = authority.lastIndexOf(':')
        return if (colonIdx >= 0) {
            val host = authority.substring(0, colonIdx).lowercase(java.util.Locale.ROOT)
            val port = authority.substring(colonIdx + 1).ifEmpty { null }
            host to port
        } else {
            authority.lowercase(java.util.Locale.ROOT) to null
        }
    }

    private fun cachedUserRegex(pattern: String): Regex? {
        synchronized(userRegexCache) {
            if (userRegexCache.containsKey(pattern)) {
                return userRegexCache[pattern]
            }
        }
        val compiled = parseRegexPattern(pattern)
        synchronized(userRegexCache) {
            userRegexCache[pattern] = compiled
        }
        return compiled
    }

    private fun parseRegexComponents(pattern: String): Pair<String, String>? {
        if (pattern.length > MAX_USER_REGEX_LENGTH) return null
        val lastSlash = pattern.lastIndexOf('/')
        if (lastSlash <= 0) return null
        val flagsPart = pattern.substring(lastSlash + 1)
        if (flagsPart.any { it !in setOf('i', 'm', 's', 'g', 'u', 'y') }) {
            return null
        }
        val body = pattern.substring(1, lastSlash)
        if (hasUnsafeNestedQuantifiers(body)) return null
        return body to flagsPart
    }

    private fun hasUnsafeNestedQuantifiers(patternBody: String): Boolean {
        if (patternBody.isEmpty()) return false
        val groupHasQuantifier = ArrayDeque<Boolean>()
        val groupHasAlternation = ArrayDeque<Boolean>()
        var i = 0
        while (i < patternBody.length) {
            val c = patternBody[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == '[') {
                i++
                while (i < patternBody.length) {
                    val ch = patternBody[i]
                    if (ch == '\\') {
                        i += 2
                        continue
                    }
                    if (ch == ']') break
                    i++
                }
                i++
                continue
            }
            when (c) {
                '(' -> {
                    groupHasQuantifier.addLast(false)
                    groupHasAlternation.addLast(false)
                }
                '|' -> {
                    if (groupHasAlternation.isNotEmpty()) {
                        groupHasAlternation.removeLast()
                        groupHasAlternation.addLast(true)
                    }
                }
                ')' -> {
                    val innerQuantified = if (groupHasQuantifier.isEmpty()) false else groupHasQuantifier.removeLast()
                    val innerAlternation = if (groupHasAlternation.isEmpty()) false else groupHasAlternation.removeLast()
                    val next = patternBody.getOrNull(i + 1)
                    val outerQuantified = next == '*' || next == '+' || next == '{'
                    if ((innerQuantified || innerAlternation) && outerQuantified) return true
                    if (innerQuantified || outerQuantified) {
                        if (groupHasQuantifier.isNotEmpty()) {
                            groupHasQuantifier.removeLast()
                            groupHasQuantifier.addLast(true)
                        }
                    }
                }
                '*', '+' -> {
                    if (groupHasQuantifier.isNotEmpty()) {
                        groupHasQuantifier.removeLast()
                        groupHasQuantifier.addLast(true)
                    }
                }
                '{' -> {
                    val close = patternBody.indexOf('}', startIndex = i + 1)
                    if (close > i + 1 && patternBody.substring(i + 1, close).any { it == ',' || it.isDigit() }) {
                        if (groupHasQuantifier.isNotEmpty()) {
                            groupHasQuantifier.removeLast()
                            groupHasQuantifier.addLast(true)
                        }
                    }
                }
            }
            i++
        }
        return false
    }

    private fun parseRegexPattern(pattern: String): Regex? {
        val (body, flagsPart) = parseRegexComponents(pattern) ?: return null
        val options = buildSet {
            if ('i' in flagsPart) add(RegexOption.IGNORE_CASE)
            if ('m' in flagsPart) add(RegexOption.MULTILINE)
            if ('s' in flagsPart) add(RegexOption.DOT_MATCHES_ALL)
        }
        return runCatching { Regex(body, options) }.getOrNull()
    }

    private fun parseStrictMatchPattern(pattern: String): MatchPattern? {
        val schemeEnd = pattern.indexOf("://")
        if (schemeEnd <= 0) return null

        val scheme = pattern.substring(0, schemeEnd).lowercase(java.util.Locale.ROOT)
        if (scheme !in setOf("*", "http", "https")) return null

        val afterScheme = pattern.substring(schemeEnd + 3)
        val slashIdx = afterScheme.indexOf('/')
        if (slashIdx <= 0) return null

        val hostWithPort = afterScheme.substring(0, slashIdx)
        val pathPattern = afterScheme.substring(slashIdx)
        val (host, port) = splitHostAndPort(hostWithPort) ?: return null
        if (host.isEmpty()) return null

        val validHost = host == "*" ||
            (host.startsWith("*.") && host.length > 2 && !host.substring(2).contains('*')) ||
            !host.contains('*')
        if (!validHost) return null

        return MatchPattern(scheme = scheme, host = host, port = port, pathPattern = pathPattern)
    }

    private fun defaultPortFor(scheme: String): String? = when (scheme) {
        "http" -> "80"
        "https" -> "443"
        else -> null
    }

    private fun matchesStrictPattern(pattern: MatchPattern, url: UrlParts): Boolean {
        val schemeOk = when (pattern.scheme) {
            "*" -> url.scheme == "http" || url.scheme == "https"
            else -> url.scheme == pattern.scheme
        }
        if (!schemeOk) return false

        val hostOk = when {
            pattern.host == "*" -> true
            pattern.host.startsWith("*.") -> {
                val baseDomain = pattern.host.removePrefix("*.")
                url.host == baseDomain || url.host.endsWith(".$baseDomain")
            }
            else -> url.host == pattern.host
        }
        if (!hostOk) return false

        if (pattern.port != null && pattern.port != "*") {
            val effectiveUrlPort = url.port ?: defaultPortFor(url.scheme)
            if (pattern.port != effectiveUrlPort) {
                return false
            }
        }

        return wildcardGlobMatches(pattern.pathPattern, url.pathAndQuery)
    }

    private fun matchesSplitGlobUrl(
        pattern: String,
        schemeEnd: Int,
        url: UrlParts
    ): Boolean {
        val patScheme = pattern.substring(0, schemeEnd).lowercase(java.util.Locale.ROOT)
        val patRest = pattern.substring(schemeEnd + 3)
        if (!wildcardSegmentMatches(patScheme, url.scheme, starReplacement = "[^:/]*")) {
            return false
        }
        val urlAuthority = if (url.port != null) "${url.host}:${url.port}" else url.host
        val slashIdx = patRest.indexOf('/')
        if (slashIdx >= 0) {
            val patAuthority = patRest.substring(0, slashIdx).lowercase(java.util.Locale.ROOT)
            val patPath = patRest.substring(slashIdx)
            if (!wildcardSegmentMatches(patAuthority, urlAuthority, starReplacement = "[^/]*")) {
                return false
            }
            return wildcardGlobMatches(patPath, url.pathAndQuery)
        }
        if (patRest == "*") {
            return true
        }
        if (patRest.endsWith("*")) {
            val prefix = patRest.removeSuffix("*").lowercase(java.util.Locale.ROOT)
            val authorityAndPath = "$urlAuthority${url.pathAndQuery}"
            val collapsedPrefix = collapseRepeatedStars(prefix)
            val cacheKey = "prefix:$collapsedPrefix"
            val regex = synchronized(globRegexCache) {
                globRegexCache.getOrPut(cacheKey) {
                    val prefixBody = globToRegexBody(collapsedPrefix, "[^/]*")
                    Regex("^$prefixBody.*$", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                }
            }
            return regex.matches(authorityAndPath)
        }
        return url.pathAndQuery == "/" &&
            wildcardSegmentMatches(patRest.lowercase(java.util.Locale.ROOT), urlAuthority, starReplacement = "[^/]*")
    }

    private fun collapseRepeatedStars(glob: String): String {
        val out = StringBuilder(glob.length)
        var prevStar = false
        for (ch in glob) {
            if (ch == '*') {
                if (!prevStar) out.append('*')
                prevStar = true
            } else {
                out.append(ch)
                prevStar = false
            }
        }
        return out.toString()
    }

    private fun escapeJsRegexLiteral(literal: String): String = buildString(literal.length + 8) {
        for (ch in literal) {
            when (ch) {
                '.', '*', '+', '?', '^', '$', '{', '}', '(', ')', '|', '[', ']', '\\', '/' -> {
                    append('\\').append(ch)
                }
                else -> append(ch)
            }
        }
    }

    private fun globToJsRegexBody(collapsedGlob: String, starReplacement: String): String = buildString {
        val parts = collapsedGlob.split('*')
        parts.forEachIndexed { index, literal ->
            if (literal.isNotEmpty()) {
                append(escapeJsRegexLiteral(literal))
            }
            if (index < parts.lastIndex) {
                append(starReplacement)
            }
        }
    }

    private fun globToRegexBody(collapsedGlob: String, starReplacement: String): String = buildString {
        val parts = collapsedGlob.split('*')
        parts.forEachIndexed { index, literal ->
            if (literal.isNotEmpty()) {
                append(Regex.escape(literal))
            }
            if (index < parts.lastIndex) {
                append(starReplacement)
            }
        }
    }

    private fun wildcardSegmentMatches(
        glob: String,
        value: String,
        starReplacement: String
    ): Boolean {
        val collapsed = collapseRepeatedStars(glob)
        val cacheKey = "seg:$starReplacement:$collapsed"
        val regex = synchronized(globRegexCache) {
            globRegexCache.getOrPut(cacheKey) {
                val body = globToRegexBody(collapsed, starReplacement)
                Regex("^$body$", RegexOption.DOT_MATCHES_ALL)
            }
        }
        return regex.matches(value)
    }

    private fun wildcardGlobMatches(glob: String, value: String): Boolean {
        val collapsed = collapseRepeatedStars(glob)
        val regex = synchronized(globRegexCache) {
            globRegexCache.getOrPut(collapsed) {
                val body = globToRegexBody(collapsed, ".*")
                Regex("^$body$", RegexOption.DOT_MATCHES_ALL)
            }
        }
        return regex.matches(value)
    }

    private const val MAX_USER_REGEX_LENGTH = 256
}
