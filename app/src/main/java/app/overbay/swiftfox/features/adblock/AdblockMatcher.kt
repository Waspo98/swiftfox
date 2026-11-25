package app.overbay.swiftfox.features.adblock

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Serializable
data class CosmeticPayload(
    val css: String = "",
    val injectedScript: String = "",
    val generichide: Boolean = false,
    val exceptions: List<String> = emptyList()
)

interface AdblockMatcher {
    fun checkNetworkRequest(url: String, sourceUrl: String, requestType: String): Boolean
    /**
     * Tri-state check used by [AdblockEngine] to avoid fail-open races across matcher swaps:
     * `1` = blocked, `0` = allowed, `-1` = matcher handle was already closed.
     */
    fun checkNetworkRequestTriState(
        url: String,
        sourceUrl: String,
        requestType: String,
        requestTypeCode: Int = NativeAdblockBridge.codeForRequestType(requestType)
    ): Int = if (checkNetworkRequest(url, sourceUrl, requestType)) 1 else 0

    fun cosmeticResources(url: String): CosmeticPayload
    fun hiddenClassIdCss(
        classes: List<String>,
        ids: List<String>,
        exceptions: Set<String>
    ): String = ""

    fun hiddenClassIdCssFromJson(
        classesJson: String,
        idsJson: String,
        exceptions: Set<String>,
        maxTokens: Int = 1500
    ): String {
        val classes = runCatching {
            NativeAdblockMatcher.sharedJson.decodeFromString<List<String>>(classesJson).take(maxTokens)
        }.getOrDefault(emptyList())
        val ids = runCatching {
            NativeAdblockMatcher.sharedJson.decodeFromString<List<String>>(idsJson).take(maxTokens)
        }.getOrDefault(emptyList())
        if (classes.isEmpty() && ids.isEmpty()) return ""
        return hiddenClassIdCss(classes, ids, exceptions)
    }

    fun serializeToFile(path: String): Boolean = false
    fun close()
}

/**
 * Thread-safe wrapper around a heap-allocated Rust `adblock::Engine` pointer.
 * Uses a read-write lock so concurrent `shouldInterceptRequest` lookups can execute
 * in parallel while `close()` waits for any active native calls to finish.
 */
class NativeAdblockMatcher(
    private var handle: Long
) : AdblockMatcher {

    private val lock = ReentrantReadWriteLock()

    override fun checkNetworkRequest(url: String, sourceUrl: String, requestType: String): Boolean =
        checkNetworkRequestTriState(
            url = url,
            sourceUrl = sourceUrl,
            requestType = requestType,
            requestTypeCode = NativeAdblockBridge.codeForRequestType(requestType)
        ) == 1

    override fun checkNetworkRequestTriState(
        url: String,
        sourceUrl: String,
        requestType: String,
        requestTypeCode: Int
    ): Int = lock.read {
        val active = handle
        if (active == 0L || !NativeAdblockBridge.isLoaded) return -1
        if (NativeAdblockBridge.nativeCheckNetworkRequestCode(active, url, sourceUrl, requestTypeCode)) 1 else 0
    }

    override fun cosmeticResources(url: String): CosmeticPayload {
        val rawJson = lock.read {
            if (handle == 0L || !NativeAdblockBridge.isLoaded) return CosmeticPayload()
            NativeAdblockBridge.nativeCosmeticForUrl(handle, url).orEmpty()
        }
        if (rawJson.isBlank()) return CosmeticPayload()
        return runCatching { sharedJson.decodeFromString<CosmeticPayload>(rawJson) }
            .getOrDefault(CosmeticPayload())
    }

    override fun hiddenClassIdCss(
        classes: List<String>,
        ids: List<String>,
        exceptions: Set<String>
    ): String {
        if (classes.isEmpty() && ids.isEmpty()) return ""
        val classesJson = sharedJson.encodeToString(classes)
        val idsJson = sharedJson.encodeToString(ids)
        return hiddenClassIdCssFromJson(classesJson, idsJson, exceptions)
    }

    override fun hiddenClassIdCssFromJson(
        classesJson: String,
        idsJson: String,
        exceptions: Set<String>,
        maxTokens: Int
    ): String {
        if ((classesJson.isBlank() || classesJson == "[]") && (idsJson.isBlank() || idsJson == "[]")) {
            return ""
        }
        val exceptionsJson = if (exceptions.isEmpty()) "[]" else sharedJson.encodeToString(exceptions.toList())
        return lock.read {
            if (handle == 0L || !NativeAdblockBridge.isLoaded) return ""
            NativeAdblockBridge.nativeHiddenClassIdSelectors(
                handle = handle,
                classesJson = classesJson,
                idsJson = idsJson,
                exceptionsJson = exceptionsJson
            ).orEmpty()
        }
    }

    override fun serializeToFile(path: String): Boolean =
        lock.read {
            if (handle == 0L || !NativeAdblockBridge.isLoaded) return false
            NativeAdblockBridge.nativeSerializeToFile(handle, path)
        }

    override fun close() {
        lock.write {
            val active = handle
            if (active != 0L) {
                handle = 0L
                if (NativeAdblockBridge.isLoaded) {
                    NativeAdblockBridge.nativeDestroy(active)
                }
            }
        }
    }

    companion object {
        internal val sharedJson = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Lightweight pure-Kotlin matcher supporting `||domain^`, `@@||exception^`,
 * `$third-party`/`$1p`, `$domain=`, resource-type filters, `##selector`, and `domain##selector`
 * for JVM unit tests and environments where the native JNI `.so` is unavailable.
 */
class FallbackKotlinAdblockMatcher(rulesText: String) : AdblockMatcher {

    private data class NetworkRule(
        val domain: String,
        val pathPrefix: String?,
        val pathRequiresSeparatorEnd: Boolean,
        val thirdPartyOnly: Boolean,
        val firstPartyOnly: Boolean,
        val isImportant: Boolean,
        val includedSourceDomains: List<String>,
        val excludedSourceDomains: List<String>,
        val includedTypes: Set<String>,
        val excludedTypes: Set<String>
    )

    private data class DomainCosmeticRule(
        val includedDomains: List<String>,
        val excludedDomains: List<String>,
        val selector: String
    )

    private val blockRulesByDomain = HashMap<String, MutableList<NetworkRule>>()
    private val exceptionRulesByDomain = HashMap<String, MutableList<NetworkRule>>()
    private val globalHideSelectors = LinkedHashSet<String>()
    private val globalUnhideSelectors = HashSet<String>()
    private val domainHideRules = ArrayList<DomainCosmeticRule>()
    private val domainUnhideRules = ArrayList<DomainCosmeticRule>()

    init {
        rulesText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('!') || line.startsWith('[')) return@forEach

            if (!line.startsWith("||") && !line.startsWith("@@")) {
                val unhideIdx = line.indexOf("#@#")
                if (unhideIdx >= 0 && isDomainListPart(line.substring(0, unhideIdx))) {
                    parseCosmeticRule(
                        domainPart = line.substring(0, unhideIdx),
                        selector = line.substring(unhideIdx + 3).trim(),
                        isUnhide = true
                    )
                    return@forEach
                }
                val hideIdx = line.indexOf("##")
                if (hideIdx >= 0 && isDomainListPart(line.substring(0, hideIdx))) {
                    parseCosmeticRule(
                        domainPart = line.substring(0, hideIdx),
                        selector = line.substring(hideIdx + 2).trim(),
                        isUnhide = false
                    )
                    return@forEach
                }
            }

            parseNetworkRule(line)
        }
    }

    private fun isDomainListPart(prefix: String): Boolean {
        if (prefix.isEmpty()) return true
        return !prefix.contains("://") && !prefix.contains('/')
    }

    private fun parseCosmeticRule(domainPart: String, selector: String, isUnhide: Boolean) {
        if (!isSafeCssSelector(selector)) return
        if (domainPart.isBlank()) {
            if (isUnhide) globalUnhideSelectors.add(selector) else globalHideSelectors.add(selector)
            return
        }
        val included = ArrayList<String>()
        val excluded = ArrayList<String>()
        domainPart.split(',').forEach { rawDomain ->
            val clean = rawDomain.trim().lowercase(java.util.Locale.ROOT)
            if (clean.isEmpty()) return@forEach
            if (clean.startsWith('~')) {
                val stripped = clean.substring(1).removePrefix("www.")
                if (stripped.isNotEmpty()) excluded.add(stripped)
            } else {
                val normalized = clean.removePrefix("www.")
                if (normalized.isNotEmpty()) included.add(normalized)
            }
        }
        val rule = DomainCosmeticRule(included, excluded, selector)
        if (isUnhide) {
            domainUnhideRules.add(rule)
        } else {
            domainHideRules.add(rule)
        }
    }

    private fun parseNetworkRule(line: String) {
        val isException = line.startsWith("@@")
        val withoutException = if (isException) line.removePrefix("@@") else line
        if (!withoutException.startsWith("||")) return

        val dollarIdx = withoutException.lastIndexOf('$')
        val patternPart: String
        val optionsPart: String?
        if (dollarIdx > 2) {
            patternPart = withoutException.substring(2, dollarIdx)
            optionsPart = withoutException.substring(dollarIdx + 1)
        } else {
            patternPart = withoutException.substring(2)
            optionsPart = null
        }

        var thirdPartyOnly = false
        var firstPartyOnly = false
        var isImportant = false
        val includedSourceDomains = ArrayList<String>()
        val excludedSourceDomains = ArrayList<String>()
        val includedTypes = HashSet<String>()
        val excludedTypes = HashSet<String>()

        if (!optionsPart.isNullOrBlank()) {
            for (rawOpt in optionsPart.split(',')) {
                val opt = rawOpt.trim().lowercase(java.util.Locale.ROOT)
                if (opt.isEmpty()) continue
                when {
                    // Skip modifier rules whose semantics cannot be approximated as plain network blocks
                    opt == "badfilter" ||
                        opt.startsWith("csp=") ||
                        opt == "csp" ||
                        opt.startsWith("redirect=") ||
                        opt.startsWith("redirect-rule=") ||
                        opt.startsWith("removeparam=") ||
                        opt == "removeparam" ||
                        opt == "generichide" ||
                        opt == "elemhide" ||
                        opt == "specifichide" ||
                        opt == "popup" ||
                        opt == "popunder" -> return
                    opt == "important" -> isImportant = true
                    opt == "third-party" || opt == "3p" -> thirdPartyOnly = true
                    opt == "~third-party" || opt == "first-party" || opt == "1p" -> firstPartyOnly = true
                    opt.startsWith("domain=") -> {
                        val domainSpec = opt.removePrefix("domain=")
                        domainSpec.split('|').forEach { d ->
                            val clean = d.trim()
                            if (clean.isEmpty()) return@forEach
                            if (clean.startsWith('~')) {
                                val stripped = clean.substring(1).removePrefix("www.")
                                if (stripped.isNotEmpty()) excludedSourceDomains.add(stripped)
                            } else {
                                val normalized = clean.removePrefix("www.")
                                if (normalized.isNotEmpty()) includedSourceDomains.add(normalized)
                            }
                        }
                    }
                    else -> {
                        val negated = opt.startsWith('~')
                        val typeName = normalizeOptionResourceType(if (negated) opt.substring(1) else opt)
                            ?: return
                        if (negated) excludedTypes.add(typeName) else includedTypes.add(typeName)
                    }
                }
            }
        }

        val cleanPattern = patternPart.trimEnd('|', '*')
        val slashIdx = cleanPattern.indexOf('/')
        val caretIdx = cleanPattern.indexOf('^')
        val domainEnd = listOf(slashIdx, caretIdx).filter { it >= 0 }.minOrNull() ?: cleanPattern.length
        val rawDomain = cleanPattern.substring(0, domainEnd)
            .substringBefore(':')
            .lowercase(java.util.Locale.ROOT)
            .removeSuffix(".")
        if (rawDomain.isBlank() || rawDomain.contains('*')) return
        if (slashIdx < 0 && caretIdx in 0 until cleanPattern.length - 1) return

        val rawPath = if (slashIdx in 0 until cleanPattern.length) {
            cleanPattern.substring(slashIdx).trimEnd('*')
        } else {
            null
        }
        val pathRequiresSeparatorEnd: Boolean
        val pathPrefix: String?
        if (rawPath != null) {
            if (rawPath.endsWith('^')) {
                pathRequiresSeparatorEnd = true
                pathPrefix = rawPath.dropLast(1).ifBlank { null }
            } else {
                pathRequiresSeparatorEnd = false
                pathPrefix = rawPath.ifBlank { null }
            }
            if (pathPrefix != null && (pathPrefix.contains('*') || pathPrefix.contains('^'))) return
        } else {
            pathRequiresSeparatorEnd = false
            pathPrefix = null
        }

        val rule = NetworkRule(
            domain = rawDomain,
            pathPrefix = pathPrefix,
            pathRequiresSeparatorEnd = pathRequiresSeparatorEnd,
            thirdPartyOnly = thirdPartyOnly,
            firstPartyOnly = firstPartyOnly,
            isImportant = isImportant,
            includedSourceDomains = includedSourceDomains,
            excludedSourceDomains = excludedSourceDomains,
            includedTypes = includedTypes,
            excludedTypes = excludedTypes
        )
        val map = if (isException) exceptionRulesByDomain else blockRulesByDomain
        map.getOrPut(rawDomain) { ArrayList() }.add(rule)
    }

    private fun normalizeOptionResourceType(opt: String): String? = when (opt) {
        "script" -> "script"
        "stylesheet", "css" -> "stylesheet"
        "image" -> "image"
        "font" -> "font"
        "media" -> "media"
        "xmlhttprequest", "xhr", "fetch" -> "xmlhttprequest"
        "subdocument", "frame" -> "subdocument"
        "document", "doc" -> "document"
        "other" -> "other"
        else -> null
    }

    private data class ParsedUrlParts(
        val host: String,
        val pathAndQuery: String
    )

    override fun checkNetworkRequest(url: String, sourceUrl: String, requestType: String): Boolean {
        val reqParts = parseUrlLenient(url) ?: return false
        val reqHost = reqParts.host
        val reqPath = reqParts.pathAndQuery
        val sourceHost = parseUrlLenient(sourceUrl)?.host?.removePrefix("www.").orEmpty()
        val isThirdParty = sourceHost.isNotBlank() && !isSameSite(reqHost, sourceHost)
        val normType = requestType.lowercase(java.util.Locale.ROOT)

        val matchedBlock = firstMatchingRule(
            blockRulesByDomain, reqHost, reqPath, sourceHost, isThirdParty, normType
        ) ?: return false

        val matchedException = firstMatchingRule(
            exceptionRulesByDomain, reqHost, reqPath, sourceHost, isThirdParty, normType
        )
        if (matchedException != null && (!matchedBlock.isImportant || matchedException.isImportant)) {
            return false
        }
        return true
    }

    private fun firstMatchingRule(
        ruleMap: Map<String, List<NetworkRule>>,
        reqHost: String,
        reqPath: String,
        sourceHost: String,
        isThirdParty: Boolean,
        requestType: String
    ): NetworkRule? {
        if (ruleMap.isEmpty()) return null
        var firstNonImportant: NetworkRule? = null
        var candidate = reqHost
        while (candidate.isNotEmpty()) {
            val rules = ruleMap[candidate]
            if (rules != null) {
                for (rule in rules) {
                    if (rule.thirdPartyOnly && !isThirdParty) continue
                    if (rule.firstPartyOnly && isThirdParty) continue
                    if (rule.pathPrefix != null) {
                        if (!reqPath.startsWith(rule.pathPrefix)) continue
                        if (rule.pathRequiresSeparatorEnd &&
                            reqPath.length > rule.pathPrefix.length &&
                            !isAdblockSeparator(reqPath[rule.pathPrefix.length])
                        ) {
                            continue
                        }
                    }
                    if (rule.includedSourceDomains.isNotEmpty() || rule.excludedSourceDomains.isNotEmpty()) {
                        if (sourceHost.isBlank()) continue
                        if (!matchesDomainList(sourceHost, rule.includedSourceDomains, rule.excludedSourceDomains)) continue
                    }
                    if (rule.includedTypes.isNotEmpty() && requestType !in rule.includedTypes) continue
                    if (rule.excludedTypes.isNotEmpty() && requestType in rule.excludedTypes) continue
                    if (rule.isImportant) return rule
                    if (firstNonImportant == null) firstNonImportant = rule
                }
            }
            val dotIdx = candidate.indexOf('.')
            if (dotIdx < 0) break
            candidate = candidate.substring(dotIdx + 1)
        }
        return firstNonImportant
    }

    private fun isAdblockSeparator(ch: Char): Boolean {
        return ch !in 'a'..'z' &&
            ch !in 'A'..'Z' &&
            ch !in '0'..'9' &&
            ch != '_' &&
            ch != '-' &&
            ch != '.' &&
            ch != '%'
    }

    override fun cosmeticResources(url: String): CosmeticPayload {
        val rawHost = parseUrlLenient(url)?.host?.removePrefix("www.") ?: ""
        val hidden = LinkedHashSet<String>()
        val exceptions = LinkedHashSet<String>()
        exceptions.addAll(globalUnhideSelectors)

        if (rawHost.isNotBlank()) {
            for (rule in domainHideRules) {
                if (matchesDomainList(rawHost, rule.includedDomains, rule.excludedDomains)) {
                    hidden.add(rule.selector)
                }
            }
            for (rule in domainUnhideRules) {
                if (matchesDomainList(rawHost, rule.includedDomains, rule.excludedDomains)) {
                    hidden.remove(rule.selector)
                    exceptions.add(rule.selector)
                }
            }
        }

        if (hidden.isEmpty()) return CosmeticPayload(exceptions = exceptions.toList())

        // Emit one rule per selector so an unusual selector never invalidates siblings
        val css = buildString(hidden.size * 40) {
            hidden.forEach { selector ->
                append(selector)
                append("{display:none!important}\n")
            }
        }
        return CosmeticPayload(css = css, exceptions = exceptions.toList())
    }

    override fun hiddenClassIdCss(
        classes: List<String>,
        ids: List<String>,
        exceptions: Set<String>
    ): String {
        if (globalHideSelectors.isEmpty()) return ""
        val matched = LinkedHashSet<String>()
        for (cls in classes) {
            val sel = ".$cls"
            if (sel in globalHideSelectors && sel !in globalUnhideSelectors && sel !in exceptions) {
                matched.add(sel)
            }
        }
        for (id in ids) {
            val sel = "#$id"
            if (sel in globalHideSelectors && sel !in globalUnhideSelectors && sel !in exceptions) {
                matched.add(sel)
            }
        }
        if (matched.isEmpty()) return ""
        return buildString {
            matched.forEach { sel ->
                append(sel)
                append("{display:none!important}\n")
            }
        }
    }

    private fun matchesDomainList(
        host: String,
        included: List<String>,
        excluded: List<String>
    ): Boolean {
        if (excluded.any { host == it || host.endsWith(".$it") }) return false
        if (included.isEmpty()) return true
        return included.any { host == it || host.endsWith(".$it") }
    }

    override fun close() = Unit

    private fun parseUrlLenient(url: String): ParsedUrlParts? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val schemeIdx = trimmed.indexOf("://")
        if (schemeIdx <= 0) return null
        val afterScheme = trimmed.substring(schemeIdx + 3)
        val noFragment = afterScheme.substringBefore('#')
        val pathStart = noFragment.indexOfAny(PATH_OR_QUERY_CHARS)
        val authority = (if (pathStart >= 0) noFragment.substring(0, pathStart) else noFragment)
            .substringAfterLast('@')
        val hostPart = if (authority.startsWith('[')) {
            authority.substringBefore(']').removePrefix("[")
        } else {
            authority.substringBefore(':')
        }.lowercase(java.util.Locale.ROOT).trim()
        if (hostPart.isBlank()) return null
        val tail = if (pathStart >= 0) noFragment.substring(pathStart) else "/"
        val pathAndQuery = if (tail.startsWith("?")) "/$tail" else tail.ifEmpty { "/" }
        return ParsedUrlParts(host = hostPart, pathAndQuery = pathAndQuery)
    }

    private fun isSameSite(hostA: String, hostB: String): Boolean {
        if (hostA == hostB) return true
        val regA = registrableDomain(hostA)
        val regB = registrableDomain(hostB)
        return regA.isNotEmpty() && regA == regB
    }

    private fun registrableDomain(host: String): String {
        val clean = host.lowercase(java.util.Locale.ROOT).trimEnd('.')
        val parts = clean.split('.')
        if (parts.size <= 2) return clean
        val lastTwo = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        return if (lastTwo in MULTI_PART_PUBLIC_SUFFIXES && parts.size >= 3) {
            "${parts[parts.size - 3]}.$lastTwo"
        } else {
            lastTwo
        }
    }

    companion object {
        private val PATH_OR_QUERY_CHARS = charArrayOf('/', '?')
        private val MULTI_PART_PUBLIC_SUFFIXES = setOf(
            "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk",
            "com.au", "net.au", "org.au", "edu.au", "gov.au",
            "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp",
            "co.nz", "net.nz", "org.nz",
            "co.kr", "or.kr", "ne.kr",
            "com.br", "net.br", "org.br",
            "com.mx", "org.mx", "gob.mx",
            "co.in", "net.in", "org.in",
            "com.cn", "net.cn", "org.cn",
            "com.tw", "org.tw", "com.hk", "com.sg",
            "co.za", "com.tr", "com.ar", "co.il", "com.ua"
        )

        internal fun isSafeCssSelector(raw: String): Boolean {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > 2048) return false
            if (trimmed.startsWith("@") || trimmed.startsWith("+js(") || trimmed.startsWith("^")) return false
            if (trimmed.contains('{') || trimmed.contains('}') || trimmed.contains(';') ||
                trimmed.contains('\n') || trimmed.contains('\r')
            ) {
                return false
            }
            val lower = trimmed.lowercase(java.util.Locale.ROOT)
            if (trimmed.contains("/*") ||
                trimmed.contains("*/") ||
                lower.contains("</style") ||
                lower.contains(":has-text(") ||
                lower.contains(":-abp-") ||
                lower.contains(":upward(") ||
                lower.contains(":style(") ||
                lower.contains(":xpath(") ||
                lower.contains(":matches-path(") ||
                lower.contains(":matches-css") ||
                lower.contains(":min-text-length(") ||
                lower.contains(":watch-attr(") ||
                lower.contains(":remove(")
            ) {
                return false
            }
            return true
        }
    }
}
