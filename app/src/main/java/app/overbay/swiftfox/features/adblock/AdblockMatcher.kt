package app.overbay.swiftfox.features.adblock

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Serializable
data class CosmeticPayload(
    val css: String = "",
    val injectedScript: String = "",
    val generichide: Boolean = false
)

interface AdblockMatcher {
    fun checkNetworkRequest(url: String, sourceUrl: String, requestType: String): Boolean
    fun cosmeticResources(url: String): CosmeticPayload
    fun serialize(): ByteArray?
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
        lock.read {
            if (handle == 0L || !NativeAdblockBridge.isLoaded) return false
            NativeAdblockBridge.nativeCheckNetworkRequest(handle, url, sourceUrl, requestType)
        }

    override fun cosmeticResources(url: String): CosmeticPayload {
        val rawJson = lock.read {
            if (handle == 0L || !NativeAdblockBridge.isLoaded) return CosmeticPayload()
            NativeAdblockBridge.nativeCosmeticForUrl(handle, url)
        }
        if (rawJson.isBlank()) return CosmeticPayload()
        return runCatching { json.decodeFromString<CosmeticPayload>(rawJson) }
            .getOrDefault(CosmeticPayload())
    }

    override fun serialize(): ByteArray? =
        lock.read {
            if (handle == 0L || !NativeAdblockBridge.isLoaded) return null
            NativeAdblockBridge.nativeSerialize(handle)
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
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Lightweight pure-Kotlin matcher supporting `||domain^`, `@@||exception^`,
 * `$third-party`/`$1p`, `##selector`, and `domain##selector` for JVM unit tests
 * and environments where the native JNI `.so` is unavailable.
 */
class FallbackKotlinAdblockMatcher(rulesText: String) : AdblockMatcher {

    private data class NetworkRule(
        val domain: String,
        val pathPrefix: String?,
        val thirdPartyOnly: Boolean,
        val firstPartyOnly: Boolean
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
        if (selector.isBlank()) return
        if (domainPart.isBlank()) {
            if (isUnhide) globalUnhideSelectors.add(selector) else globalHideSelectors.add(selector)
            return
        }
        val included = ArrayList<String>()
        val excluded = ArrayList<String>()
        domainPart.split(',').forEach { rawDomain ->
            val d = rawDomain.trim().lowercase().removePrefix("www.")
            if (d.isEmpty()) return@forEach
            if (d.startsWith('~')) {
                val stripped = d.removePrefix("~")
                if (stripped.isNotEmpty()) excluded.add(stripped)
            } else {
                included.add(d)
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
        if (!optionsPart.isNullOrBlank()) {
            optionsPart.split(',').forEach { rawOpt ->
                when (rawOpt.trim().lowercase()) {
                    "third-party", "3p" -> thirdPartyOnly = true
                    "~third-party", "first-party", "1p" -> firstPartyOnly = true
                }
            }
        }

        val cleanPattern = patternPart.trimEnd('|', '*')
        val slashIdx = cleanPattern.indexOf('/')
        val caretIdx = cleanPattern.indexOf('^')
        val domainEnd = listOf(slashIdx, caretIdx).filter { it >= 0 }.minOrNull() ?: cleanPattern.length
        val rawDomain = cleanPattern.substring(0, domainEnd)
            .substringBefore(':')
            .lowercase()
            .removeSuffix(".")
        if (rawDomain.isBlank() || rawDomain.contains('*')) return

        val pathPrefix = if (slashIdx in 0 until cleanPattern.length) {
            cleanPattern.substring(slashIdx).trimEnd('^', '*').ifBlank { null }
        } else {
            null
        }

        val rule = NetworkRule(
            domain = rawDomain,
            pathPrefix = pathPrefix,
            thirdPartyOnly = thirdPartyOnly,
            firstPartyOnly = firstPartyOnly
        )
        val map = if (isException) exceptionRulesByDomain else blockRulesByDomain
        map.getOrPut(rawDomain) { ArrayList() }.add(rule)
    }

    override fun checkNetworkRequest(url: String, sourceUrl: String, requestType: String): Boolean {
        val reqUri = parseUri(url) ?: return false
        val reqHost = reqUri.host?.lowercase()?.trim('[', ']') ?: return false
        val reqPath = buildString {
            append(reqUri.rawPath ?: "/")
            reqUri.rawQuery?.let {
                append('?')
                append(it)
            }
        }
        val sourceHost = parseUri(sourceUrl)?.host?.lowercase()?.trim('[', ']').orEmpty()
        val isThirdParty = sourceHost.isNotBlank() && !isSameSite(reqHost, sourceHost)

        if (matchesAnyRule(exceptionRulesByDomain, reqHost, reqPath, isThirdParty)) {
            return false
        }
        return matchesAnyRule(blockRulesByDomain, reqHost, reqPath, isThirdParty)
    }

    private fun matchesAnyRule(
        ruleMap: Map<String, List<NetworkRule>>,
        reqHost: String,
        reqPath: String,
        isThirdParty: Boolean
    ): Boolean {
        if (ruleMap.isEmpty()) return false
        var candidate = reqHost
        while (candidate.isNotEmpty()) {
            val rules = ruleMap[candidate]
            if (rules != null) {
                for (rule in rules) {
                    if (rule.thirdPartyOnly && !isThirdParty) continue
                    if (rule.firstPartyOnly && isThirdParty) continue
                    if (rule.pathPrefix != null && !reqPath.startsWith(rule.pathPrefix)) continue
                    return true
                }
            }
            val dotIdx = candidate.indexOf('.')
            if (dotIdx < 0) break
            candidate = candidate.substring(dotIdx + 1)
        }
        return false
    }

    override fun cosmeticResources(url: String): CosmeticPayload {
        val rawHost = parseUri(url)?.host?.lowercase()?.removePrefix("www.") ?: ""
        val hidden = LinkedHashSet<String>()
        hidden.addAll(globalHideSelectors)
        hidden.removeAll(globalUnhideSelectors)

        if (rawHost.isNotBlank()) {
            for (rule in domainHideRules) {
                if (matchesDomainList(rawHost, rule.includedDomains, rule.excludedDomains)) {
                    hidden.add(rule.selector)
                }
            }
            for (rule in domainUnhideRules) {
                if (matchesDomainList(rawHost, rule.includedDomains, rule.excludedDomains)) {
                    hidden.remove(rule.selector)
                }
            }
        }

        if (hidden.isEmpty()) return CosmeticPayload()

        val css = buildString {
            hidden.chunked(400).forEach { chunk ->
                append(chunk.joinToString(","))
                append("{display:none!important}\n")
            }
        }
        return CosmeticPayload(css = css)
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

    override fun serialize(): ByteArray? = null

    override fun close() = Unit

    private fun parseUri(url: String): URI? =
        runCatching { URI(url) }.getOrNull()

    private fun isSameSite(hostA: String, hostB: String): Boolean {
        if (hostA == hostB) return true
        val rootA = hostA.split('.').takeLast(2).joinToString(".")
        val rootB = hostB.split('.').takeLast(2).joinToString(".")
        return rootA.isNotEmpty() && rootA == rootB
    }
}
