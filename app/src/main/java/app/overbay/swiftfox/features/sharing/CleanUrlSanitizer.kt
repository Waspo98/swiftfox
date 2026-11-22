package app.overbay.swiftfox.features.sharing

import java.net.URI

data class SanitizedUrlResult(
    val url: String,
    val strippedCount: Int
)

object CleanUrlSanitizer {

    private val exactTrackingParams = setOf(
        "fbclid",
        "gclid",
        "dclid",
        "gbraid",
        "wbraid",
        "igshid",
        "igsh",
        "mc_cid",
        "mc_eid",
        "si",
        "ref_src",
        "ref_url",
        "spm",
        "yclid",
        "_hsenc",
        "_hsmi",
        "mkt_tok",
        "vero_id",
        "oly_anon_id",
        "oly_enc_id",
        "rb_clickid",
        "wickedid",
        "msclkid",
        "twclid",
        "ttclid",
        "scid",
        "s_cid",
        "feature"
    )

    private val trackingPrefixes = listOf(
        "utm_",
        "ga_",
        "hsa_",
        "mtm_",
        "pk_",
        "icid"
    )

    fun sanitize(rawUrl: String): SanitizedUrlResult {
        val trimmed = rawUrl.trim()
        val queryStart = trimmed.indexOf('?')
        if (queryStart < 0) return SanitizedUrlResult(trimmed, 0)

        val fragmentStart = trimmed.indexOf('#', queryStart)
        val base = trimmed.substring(0, queryStart)
        val query = if (fragmentStart >= 0) {
            trimmed.substring(queryStart + 1, fragmentStart)
        } else {
            trimmed.substring(queryStart + 1)
        }
        val fragment = if (fragmentStart >= 0) trimmed.substring(fragmentStart) else ""

        if (query.isEmpty()) return SanitizedUrlResult(trimmed, 0)

        val host = runCatching { URI(trimmed).host?.lowercase().orEmpty() }.getOrDefault("")
        val kept = mutableListOf<String>()
        var stripped = 0

        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = pair.substringBefore('=').lowercase()
            val isYouTubeFeature = key == "feature" && (host.contains("youtube.com") || host.contains("youtu.be"))
            val isTracking = (key in exactTrackingParams && (key != "feature" || isYouTubeFeature)) ||
                trackingPrefixes.any { key.startsWith(it) }
            if (isTracking) {
                stripped++
            } else {
                kept += pair
            }
        }

        if (stripped == 0) return SanitizedUrlResult(trimmed, 0)

        val rebuilt = buildString {
            append(base)
            if (kept.isNotEmpty()) {
                append('?')
                append(kept.joinToString("&"))
            }
            append(fragment)
        }
        return SanitizedUrlResult(rebuilt, stripped)
    }
}
