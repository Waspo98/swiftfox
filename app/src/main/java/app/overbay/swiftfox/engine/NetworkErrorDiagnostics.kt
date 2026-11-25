package app.overbay.swiftfox.engine

import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ConnectionDiagnosticResult(
    val resolvedIps: List<String>,
    val hasSinkholeOrLoopbackIp: Boolean,
    val directProbeSummary: String?
)

/**
 * Lightweight on-device diagnostics for main-frame WebView connection failures
 * (such as `net::ERR_CONNECTION_REFUSED`) to distinguish device/Private-DNS/VPN sinkholes
 * from WebView-specific network refusals.
 */
object NetworkErrorDiagnostics {

    private val rawAuthorityRegex = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]+)""")

    /**
     * Extracts the raw hostname (preserving `www.` for accurate DNS lookups) even when [url]
     * contains unencoded spaces, pipes, braces, or brackets in its path or query string.
     */
    internal fun extractRawHost(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        val fromHttpUrl = trimmed.toHttpUrlOrNull()?.host
        if (!fromHttpUrl.isNullOrBlank()) {
            return fromHttpUrl.trim().removePrefix("[").removeSuffix("]").lowercase(Locale.ROOT)
        }
        val authority = rawAuthorityRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: return ""
        val hostPort = authority.substringAfterLast('@').trim()
        val rawHost = if (hostPort.startsWith("[")) {
            hostPort.substringAfter('[').substringBefore(']')
        } else {
            hostPort.substringBefore(':')
        }
        return rawHost.trim().lowercase(Locale.ROOT)
    }

    /**
     * Builds an origin-only HTTP(S) URL (`scheme://host[:port]/`) for the direct reachability
     * probe so path/query tokens are never resent outside the WebView and unencoded query
     * characters cannot break OkHttp URL parsing.
     */
    internal fun buildOriginProbeUrl(url: String): String? {
        val trimmed = url.trim()
        val scheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> "https"
            trimmed.startsWith("http://", ignoreCase = true) -> "http"
            else -> return null
        }
        val parsed = trimmed.toHttpUrlOrNull()
        if (parsed != null) {
            val hostPart = if (parsed.host.contains(':')) "[${parsed.host}]" else parsed.host
            val defaultPort = if (scheme == "https") 443 else 80
            val portPart = if (parsed.port != defaultPort) ":${parsed.port}" else ""
            return "$scheme://$hostPart$portPart/"
        }
        val authority = rawAuthorityRegex.find(trimmed)?.groupValues?.getOrNull(1)
            ?.substringAfterLast('@')
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.any(Char::isWhitespace) }
            ?: return null
        return "$scheme://$authority/".toHttpUrlOrNull()?.toString()
    }

    internal fun isSinkholeOrUnexpectedLocalIp(host: String, addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress || addr.isLoopbackAddress) return true
        val hostIsLocal = OmniboxEngine.isLocalHostOrLan(host)
        if (!hostIsLocal && OmniboxEngine.isLocalOrPrivateInetAddress(addr)) {
            return true
        }
        return false
    }

    internal fun formatDiagnosticMessage(
        host: String,
        description: String,
        resolvedIps: List<String>?,
        hasSinkholeOrLoopbackIp: Boolean,
        directProbeSummary: String?
    ): String {
        val cleanHost = host.ifBlank { "this site" }
        if (resolvedIps == null) {
            return "Checking device DNS for $cleanHost…"
        }
        if (resolvedIps.isEmpty()) {
            return "Device DNS returned no IP addresses for $cleanHost."
        }
        val ipList = resolvedIps.take(3).joinToString(", ")
        if (hasSinkholeOrLoopbackIp) {
            return "Device DNS resolved $cleanHost to $ipList (blocked or redirected by Private DNS, VPN, or hosts; browsers with built-in Secure DNS may bypass this)."
        }
        return buildString {
            append("Device DNS: ").append(ipList)
            if (!directProbeSummary.isNullOrBlank()) {
                append(" · Direct probe: ").append(directProbeSummary)
            } else if (description.isNotBlank()) {
                append(" · ").append(description)
            }
        }
    }

    suspend fun diagnose(
        url: String,
        description: String,
        baseClient: OkHttpClient,
        runDirectProbe: Boolean
    ): ConnectionDiagnosticResult = withContext(Dispatchers.IO) {
        val rawHost = extractRawHost(url)
        if (rawHost.isBlank()) {
            return@withContext ConnectionDiagnosticResult(
                resolvedIps = emptyList(),
                hasSinkholeOrLoopbackIp = false,
                directProbeSummary = null
            )
        }

        val addresses = runCatching { InetAddress.getAllByName(rawHost).toList() }
            .getOrDefault(emptyList())
        val resolvedIps = addresses.mapNotNull { it.hostAddress }.distinct()
        val sinkholed = addresses.any { isSinkholeOrUnexpectedLocalIp(rawHost, it) }

        val probeUrl = if (runDirectProbe) buildOriginProbeUrl(url) else null
        val shouldProbe = runDirectProbe &&
            probeUrl != null &&
            !sinkholed &&
            resolvedIps.isNotEmpty() &&
            !OmniboxEngine.isLocalHostOrLan(rawHost) &&
            (description.contains("ERR_CONNECTION_REFUSED", ignoreCase = true) ||
                description.contains("ERR_CONNECTION_RESET", ignoreCase = true) ||
                description.contains("ERR_CONNECTION_CLOSED", ignoreCase = true))

        val probeSummary = if (shouldProbe) {
            try {
                val probeClient = baseClient.newBuilder()
                    .callTimeout(4, TimeUnit.SECONDS)
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(4, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .build()
                val req = Request.Builder()
                    .url(probeUrl)
                    .head()
                    .build()
                val call = probeClient.newCall(req)
                val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
                try {
                    call.execute().use { resp ->
                        "HTTP ${resp.code} (reachable outside WebView)"
                    }
                } finally {
                    cancelHandle?.dispose()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                err.javaClass.simpleName + (err.message?.take(64)?.let { ": $it" } ?: "")
            }
        } else {
            null
        }

        ConnectionDiagnosticResult(
            resolvedIps = resolvedIps,
            hasSinkholeOrLoopbackIp = sinkholed,
            directProbeSummary = probeSummary
        )
    }
}
