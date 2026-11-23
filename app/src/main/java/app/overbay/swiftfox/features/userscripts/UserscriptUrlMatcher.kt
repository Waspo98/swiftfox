package app.overbay.swiftfox.features.userscripts

/**
 * Evaluates `@match`, `@include`, `@exclude`, `<all_urls>`, and `/regex/` patterns against HTTP/HTTPS URLs.
 */
object UserscriptUrlMatcher {

    private data class UrlParts(
        val scheme: String,
        val host: String,
        val port: String?,
        val pathAndQuery: String
    )

    private data class MatchPattern(
        val scheme: String,
        val host: String,
        val port: String?,
        val pathPattern: String
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

    fun patternMatches(pattern: String, url: String): Boolean {
        val trimmedPattern = pattern.trim()
        val trimmedUrl = url.trim()
        if (trimmedPattern.isEmpty() || !isHttpOrHttps(trimmedUrl)) return false

        val urlParts = parseUrlParts(trimmedUrl) ?: return false

        if (trimmedPattern == "<all_urls>") {
            return true
        }

        if (trimmedPattern.startsWith("/") && !trimmedPattern.startsWith("//")) {
            val regex = parseRegexPattern(trimmedPattern)
            if (regex != null) {
                return regex.containsMatchIn(trimmedUrl)
            }
        }

        val matchPattern = parseStrictMatchPattern(trimmedPattern)
        if (matchPattern != null) {
            return matchesStrictPattern(matchPattern, urlParts)
        }

        return wildcardGlobMatches(trimmedPattern, trimmedUrl)
    }

    private fun parseUrlParts(url: String): UrlParts? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null

        val afterScheme = url.substring(schemeEnd + 3)
        val authorityEnd = listOf(
            afterScheme.indexOf('/'),
            afterScheme.indexOf('?'),
            afterScheme.indexOf('#')
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
        return UrlParts(scheme = scheme, host = host, port = port, pathAndQuery = pathAndQuery)
    }

    private fun splitHostAndPort(authority: String): Pair<String, String?>? {
        if (authority.startsWith("[")) {
            val closeIdx = authority.indexOf(']')
            if (closeIdx < 0) return null
            val host = authority.substring(0, closeIdx + 1).lowercase()
            val rest = authority.substring(closeIdx + 1)
            val port = if (rest.startsWith(":")) rest.substring(1).ifEmpty { null } else if (rest.isEmpty()) null else return null
            return host to port
        }
        val colonIdx = authority.lastIndexOf(':')
        return if (colonIdx >= 0) {
            val host = authority.substring(0, colonIdx).lowercase()
            val port = authority.substring(colonIdx + 1).ifEmpty { null }
            host to port
        } else {
            authority.lowercase() to null
        }
    }

    private fun parseRegexPattern(pattern: String): Regex? {
        val lastSlash = pattern.lastIndexOf('/')
        if (lastSlash <= 0) return null
        val flagsPart = pattern.substring(lastSlash + 1)
        if (flagsPart.any { it !in setOf('i', 'm', 's', 'g', 'u', 'y') }) {
            return null
        }
        val body = pattern.substring(1, lastSlash)
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

        val scheme = pattern.substring(0, schemeEnd).lowercase()
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

        if (pattern.port != null && pattern.port != "*" && pattern.port != url.port) {
            return false
        }

        return wildcardGlobMatches(pattern.pathPattern, url.pathAndQuery)
    }

    private fun wildcardGlobMatches(glob: String, value: String): Boolean {
        val regexString = buildString {
            append('^')
            val parts = glob.split('*')
            parts.forEachIndexed { index, literal ->
                if (literal.isNotEmpty()) {
                    append(Regex.escape(literal))
                }
                if (index < parts.lastIndex) {
                    append(".*")
                }
            }
            append('$')
        }
        return Regex(regexString, RegexOption.DOT_MATCHES_ALL).matches(value)
    }
}
