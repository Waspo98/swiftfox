package app.overbay.swiftfox.features.webapps

import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.util.Locale
import kotlin.math.abs

/**
 * Pure Kotlin W3C Web App Manifest parser, scope checker, CSS color normalizer, and OAuth/SSO
 * navigation heuristic for Swiftfox PWAs and home-screen shortcuts.
 */
object WebAppManifestParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val NAMED_COLORS: Map<String, String> = mapOf(
        "black" to "#000000",
        "white" to "#FFFFFF",
        "red" to "#FF0000",
        "green" to "#008000",
        "blue" to "#0000FF",
        "yellow" to "#FFFF00",
        "cyan" to "#00FFFF",
        "magenta" to "#FF00FF",
        "gray" to "#808080",
        "grey" to "#808080",
        "silver" to "#C0C0C0",
        "maroon" to "#800000",
        "olive" to "#808000",
        "lime" to "#00FF00",
        "aqua" to "#00FFFF",
        "teal" to "#008080",
        "navy" to "#000080",
        "fuchsia" to "#FF00FF",
        "purple" to "#800080",
        "orange" to "#FFA500"
    )

    private val RGB_REGEX = Regex(
        """^rgba?\(\s*([0-9]{1,3})\s*[,\s]\s*([0-9]{1,3})\s*[,\s]\s*([0-9]{1,3})(?:\s*[,/]\s*([0-9.]+%?))?\s*\)$""",
        RegexOption.IGNORE_CASE
    )

    private val OAUTH_HOST_SUFFIXES = setOf(
        "accounts.google.com",
        "appleid.apple.com",
        "login.microsoftonline.com",
        "login.live.com",
        "auth0.com",
        "okta.com",
        "oktapreview.com",
        "login.yahoo.com",
        "api.twitter.com",
        "discord.com"
    )

    private val OAUTH_PATH_FRAGMENTS = listOf(
        "/oauth",
        "/oauth2",
        "/authorize",
        "/login/oauth",
        "/auth/realms/",
        "/realms/",
        "/if/flow/",
        "/saml",
        "/oidc",
        "/.well-known/",
        "/sso/"
    )

    fun parseManifest(
        rawJson: String,
        manifestUrl: String?,
        pageUrl: String,
        fallbackTitle: String = ""
    ): ParsedWebAppManifest? {
        val root = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() as? JsonObject ?: return null
        val baseForResolution = manifestUrl?.takeIf { it.isNotBlank() } ?: pageUrl

        val rawName = root.optString("name").orEmpty().trim()
        val rawShortName = root.optString("short_name").orEmpty().trim()
        val resolvedName = rawName.ifBlank { rawShortName }.ifBlank { fallbackTitle.trim() }
            .ifBlank { OmniboxEngine.extractHost(pageUrl).ifBlank { "Web App" } }
        val resolvedShortName = rawShortName.ifBlank { resolvedName }

        val rawStartUrl = root.optString("start_url")
        val resolvedStart = resolveHttpUrl(baseForResolution, rawStartUrl)
            ?.takeIf { isSameOrigin(it, pageUrl) }
            ?: pageUrl

        val defaultScope = defaultScopeForStartUrl(resolvedStart)
        val rawScope = root.optString("scope")
        val candidateScope = resolveHttpUrl(baseForResolution, rawScope)
        val resolvedScope = if (candidateScope != null && isValidScopeForStartUrl(candidateScope, resolvedStart)) {
            candidateScope
        } else {
            defaultScope
        }

        val displayMode = resolveDisplayMode(root)
        val themeColorHex = normalizeCssColorHex(root.optString("theme_color"))
        val backgroundColorHex = normalizeCssColorHex(root.optString("background_color"))

        val icons = parseIconsArray(root["icons"], baseForResolution)
        val (bestIcon, bestMaskable) = selectBestIcon(icons)

        val shortcuts = parseShortcutsArray(
            element = root["shortcuts"],
            baseUrl = baseForResolution,
            startUrl = resolvedStart,
            scopeUrl = resolvedScope
        )

        return ParsedWebAppManifest(
            manifestUrl = manifestUrl,
            name = resolvedName,
            shortName = resolvedShortName,
            startUrl = resolvedStart,
            scopeUrl = resolvedScope,
            displayMode = displayMode,
            themeColorHex = themeColorHex,
            backgroundColorHex = backgroundColorHex,
            icons = icons,
            bestIconUrl = bestIcon?.src,
            bestIconMaskable = bestMaskable,
            shortcuts = shortcuts
        )
    }

    fun buildInstallCandidate(
        meta: PageWebAppMetadata,
        manifest: ParsedWebAppManifest?,
        hasBeforeInstallPrompt: Boolean = false,
        existingInstalledAppId: String? = null
    ): PageInstallCandidate {
        val pageThemeHex = normalizeCssColorHex(meta.themeColorRaw)
        val hostFallback = OmniboxEngine.extractHost(meta.pageUrl).ifBlank { "Web App" }
        val fallbackTitle = meta.applicationName?.trim()?.takeIf { it.isNotBlank() }
            ?: meta.pageTitle.trim().takeIf { it.isNotBlank() }
            ?: hostFallback

        if (manifest != null) {
            val isMetaCapable = meta.appleMobileCapable || meta.mobileCapable
            val isStandaloneCapable = manifest.displayMode.isAppLike || isMetaCapable
            val effectiveDisplay = if (manifest.displayMode.isAppLike) {
                manifest.displayMode
            } else if (isMetaCapable) {
                WebAppDisplayMode.STANDALONE
            } else {
                WebAppDisplayMode.BROWSER
            }

            val fallbackIcon = selectBestIcon(meta.touchIcons + meta.pageIcons).first
            return PageInstallCandidate(
                pageUrl = meta.pageUrl,
                pageTitle = meta.pageTitle,
                suggestedName = manifest.name,
                suggestedShortName = manifest.shortName,
                startUrl = manifest.startUrl,
                scopeUrl = manifest.scopeUrl,
                manifestUrl = manifest.manifestUrl,
                displayMode = effectiveDisplay,
                isStandaloneCapable = isStandaloneCapable,
                hasBeforeInstallPrompt = hasBeforeInstallPrompt,
                themeColorHex = pageThemeHex ?: manifest.themeColorHex,
                backgroundColorHex = manifest.backgroundColorHex ?: manifest.themeColorHex ?: pageThemeHex,
                bestIconUrl = manifest.bestIconUrl ?: fallbackIcon?.src,
                bestIconMaskable = if (manifest.bestIconUrl != null) manifest.bestIconMaskable else false,
                shortcuts = manifest.shortcuts,
                existingInstalledAppId = existingInstalledAppId
            )
        }

        val isMetaCapable = meta.appleMobileCapable || meta.mobileCapable
        val (bestMetaIcon, _) = selectBestIcon(meta.touchIcons + meta.pageIcons)
        val defaultScope = defaultScopeForStartUrl(meta.pageUrl)
        return PageInstallCandidate(
            pageUrl = meta.pageUrl,
            pageTitle = meta.pageTitle,
            suggestedName = fallbackTitle,
            suggestedShortName = fallbackTitle,
            startUrl = meta.pageUrl,
            scopeUrl = defaultScope,
            manifestUrl = meta.manifestUrl,
            displayMode = if (isMetaCapable) WebAppDisplayMode.STANDALONE else WebAppDisplayMode.BROWSER,
            isStandaloneCapable = isMetaCapable,
            hasBeforeInstallPrompt = hasBeforeInstallPrompt,
            themeColorHex = pageThemeHex,
            backgroundColorHex = pageThemeHex,
            bestIconUrl = bestMetaIcon?.src,
            bestIconMaskable = false,
            shortcuts = emptyList(),
            existingInstalledAppId = existingInstalledAppId
        )
    }

    fun encodeShortcutsJson(shortcuts: List<WebAppShortcutItem>): String =
        runCatching { json.encodeToString(shortcuts) }.getOrDefault("[]")

    fun decodeShortcutsJson(raw: String?): List<WebAppShortcutItem> {
        if (raw.isNullOrBlank() || raw == "[]") return emptyList()
        return runCatching { json.decodeFromString<List<WebAppShortcutItem>>(raw) }.getOrDefault(emptyList())
    }

    fun resolveHttpUrl(baseUrl: String, relativeOrAbsolute: String?): String? {
        val raw = relativeOrAbsolute?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lower = raw.lowercase(Locale.ROOT)
        if (lower.startsWith("javascript:") || lower.startsWith("vbscript:") || lower.startsWith("file:")) {
            return null
        }
        return runCatching {
            val base = URI(baseUrl)
            val resolved = base.resolve(raw).normalize()
            val scheme = resolved.scheme?.lowercase(Locale.ROOT) ?: return null
            if (scheme != "http" && scheme != "https") return null
            if (resolved.host.isNullOrBlank()) return null
            resolved.toString()
        }.getOrNull()
    }

    fun extractOriginKey(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port
        val isDefault = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
        return if (port in 1..65535 && !isDefault) "$scheme://$host:$port" else "$scheme://$host"
    }

    fun isSameOrigin(urlA: String, urlB: String): Boolean {
        val a = extractOriginKey(urlA) ?: return false
        val b = extractOriginKey(urlB) ?: return false
        return a == b
    }

    fun defaultScopeForStartUrl(startUrl: String): String {
        val uri = runCatching { URI(startUrl.trim()) }.getOrNull() ?: return startUrl
        val origin = extractOriginKey(startUrl) ?: return startUrl
        val rawPath = uri.rawPath.orEmpty()
        if (rawPath.isEmpty() || rawPath == "/") return "$origin/"
        val lastSlash = rawPath.lastIndexOf('/')
        val dirPath = if (lastSlash >= 0) rawPath.substring(0, lastSlash + 1) else "/"
        return "$origin$dirPath"
    }

    fun isValidScopeForStartUrl(scopeUrl: String, startUrl: String): Boolean {
        if (!isSameOrigin(scopeUrl, startUrl)) return false
        val scopePath = normalizedPathForScopeCheck(scopeUrl)
        val startPath = normalizedPathForScopeCheck(startUrl)
        return isPathWithinScope(startPath, scopePath)
    }

    fun isUrlInWebAppScope(targetUrl: String, startUrl: String, scopeUrl: String): Boolean {
        val effectiveScope = scopeUrl.trim().ifBlank { defaultScopeForStartUrl(startUrl) }
        if (!isSameOrigin(targetUrl, effectiveScope)) return false
        val targetPath = normalizedPathForScopeCheck(targetUrl)
        val scopePath = normalizedPathForScopeCheck(effectiveScope)
        return isPathWithinScope(targetPath, scopePath)
    }

    private fun normalizedPathForScopeCheck(url: String): String {
        val path = runCatching { URI(url.trim()).normalize().rawPath.orEmpty() }.getOrDefault("")
        return if (path.isEmpty()) "/" else path
    }

    private fun isPathWithinScope(targetPath: String, scopePath: String): Boolean {
        if (scopePath == "/") return true
        if (targetPath == scopePath) return true
        if (scopePath.endsWith("/")) return targetPath.startsWith(scopePath)
        return targetPath.startsWith("$scopePath/") || targetPath.startsWith("$scopePath?")
    }

    fun isLikelyAuthOrOAuthRedirect(
        targetUrl: String,
        isRedirect: Boolean,
        hasGesture: Boolean
    ): Boolean {
        if (isRedirect || !hasGesture) return true
        val uri = runCatching { URI(targetUrl.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        if (OAUTH_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) {
            return true
        }
        val path = uri.rawPath?.lowercase(Locale.ROOT).orEmpty()
        val query = uri.rawQuery?.lowercase(Locale.ROOT).orEmpty()
        if (OAUTH_PATH_FRAGMENTS.any { path.contains(it) }) {
            return true
        }
        return query.contains("redirect_uri=") ||
            query.contains("client_id=") ||
            query.contains("response_type=") ||
            query.contains("samlrequest=")
    }

    fun normalizeCssColorHex(raw: String?): String? {
        val clean = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lower = clean.lowercase(Locale.ROOT)
        if (lower == "transparent" || lower == "inherit" || lower == "initial" || lower == "unset") {
            return null
        }
        NAMED_COLORS[lower]?.let { return it }

        if (lower.startsWith("#")) {
            val hex = lower.substring(1)
            return when (hex.length) {
                3 -> if (hex.all(::isHexDigit)) {
                    "#${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}".uppercase(Locale.ROOT)
                } else {
                    null
                }
                4 -> if (hex.all(::isHexDigit)) {
                    val a = "${hex[3]}${hex[3]}"
                    if (a == "00") null else {
                        "#$a${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}".uppercase(Locale.ROOT)
                    }
                } else {
                    null
                }
                6 -> if (hex.all(::isHexDigit)) "#${hex.uppercase(Locale.ROOT)}" else null
                8 -> if (hex.all(::isHexDigit)) {
                    val a = hex.substring(6, 8)
                    if (a == "00") null else "#${a}${hex.substring(0, 6)}".uppercase(Locale.ROOT)
                } else {
                    null
                }
                else -> null
            }
        }

        val rgbMatch = RGB_REGEX.matchEntire(lower) ?: return null
        val r = rgbMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val g = rgbMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val b = rgbMatch.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val alphaGroup = rgbMatch.groupValues[4]
        val alphaByte = if (alphaGroup.isBlank()) {
            255
        } else if (alphaGroup.endsWith("%")) {
            val pct = alphaGroup.removeSuffix("%").toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: return null
            ((pct / 100.0) * 255.0).toInt().coerceIn(0, 255)
        } else {
            val f = alphaGroup.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: return null
            (f * 255.0).toInt().coerceIn(0, 255)
        }
        if (alphaByte == 0) return null
        return if (alphaByte == 255) {
            String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b)
        } else {
            String.format(Locale.ROOT, "#%02X%02X%02X%02X", alphaByte, r, g, b)
        }
    }

    fun parseColorInt(hex: String?): Int? {
        val normalized = normalizeCssColorHex(hex) ?: return null
        val raw = normalized.removePrefix("#")
        return when (raw.length) {
            6 -> (0xFF000000L or raw.toLong(16)).toInt()
            8 -> raw.toLong(16).toInt()
            else -> null
        }
    }

    fun isLightColor(argb: Int): Boolean {
        val r = ((argb ushr 16) and 0xFF) / 255.0
        val g = ((argb ushr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance > 0.55
    }

    private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun resolveDisplayMode(root: JsonObject): WebAppDisplayMode {
        val overrides = root["display_override"] as? JsonArray
        if (overrides != null) {
            for (el in overrides) {
                val parsed = WebAppDisplayMode.fromRaw(el.optStringOrNull())
                if (parsed != WebAppDisplayMode.BROWSER || el.optStringOrNull().equals("browser", true)) {
                    return parsed
                }
            }
        }
        return WebAppDisplayMode.fromRaw(root.optString("display"))
    }

    private fun parseIconsArray(element: JsonElement?, baseUrl: String): List<ManifestIconCandidate> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val rawSrc = obj.optString("src")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val resolvedSrc = if (rawSrc.startsWith("data:image/", ignoreCase = true)) {
                rawSrc
            } else {
                resolveHttpUrl(baseUrl, rawSrc)
            } ?: return@mapNotNull null
            ManifestIconCandidate(
                src = resolvedSrc,
                sizes = obj.optString("sizes").orEmpty().trim(),
                type = obj.optString("type").orEmpty().trim(),
                purpose = obj.optString("purpose").orEmpty().trim().ifBlank { "any" }
            )
        }
    }

    private fun parseShortcutsArray(
        element: JsonElement?,
        baseUrl: String,
        startUrl: String,
        scopeUrl: String
    ): List<WebAppShortcutItem> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj.optString("name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: obj.optString("short_name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val url = resolveHttpUrl(baseUrl, obj.optString("url")) ?: return@mapNotNull null
            if (!isUrlInWebAppScope(url, startUrl, scopeUrl)) return@mapNotNull null
            val desc = obj.optString("description").orEmpty().trim()
            WebAppShortcutItem(name = name, url = url, description = desc)
        }.take(4)
    }

    internal fun selectBestIcon(candidates: List<ManifestIconCandidate>): Pair<ManifestIconCandidate?, Boolean> {
        if (candidates.isEmpty()) return null to false
        val scored = candidates.mapNotNull { icon ->
            val purposes = icon.purpose.lowercase(Locale.ROOT).split(Regex("""\s+""")).filter { it.isNotBlank() }.toSet()
            if (purposes.contains("monochrome") && !purposes.contains("any") && !purposes.contains("maskable")) {
                return@mapNotNull null
            }
            val isMaskable = purposes.contains("maskable")
            val isSvg = icon.type.equals("image/svg+xml", ignoreCase = true) ||
                icon.src.substringBefore('?').substringBefore('#').endsWith(".svg", ignoreCase = true)
            val isPngOrWebp = icon.type.equals("image/png", true) ||
                icon.type.equals("image/webp", true) ||
                icon.src.substringBefore('?').substringBefore('#').let {
                    it.endsWith(".png", true) || it.endsWith(".webp", true)
                }
            val sizePx = parseMaxIconDimension(icon.sizes)
            val sizeScore = when {
                sizePx in 192..512 -> 500 - abs(256 - sizePx) / 2
                sizePx > 512 -> 360
                sizePx in 128 until 192 -> 320 + sizePx / 4
                sizePx in 64 until 128 -> 180 + sizePx
                sizePx > 0 -> 80 + sizePx
                icon.sizes.equals("any", ignoreCase = true) -> 240
                else -> 210
            }
            val maskableBonus = if (isMaskable && (sizePx == 0 || sizePx >= 128)) 140 else 0
            val formatScore = when {
                isSvg -> -600
                isPngOrWebp -> 120
                else -> 40
            }
            val total = sizeScore + maskableBonus + formatScore
            Triple(icon, isMaskable, total)
        }
        val best = scored.maxByOrNull { it.third } ?: return candidates.first() to false
        return best.first to best.second
    }

    private fun parseMaxIconDimension(sizes: String): Int {
        if (sizes.isBlank()) return 0
        var maxEdge = 0
        sizes.trim().split(Regex("""\s+""")).forEach { token ->
            val parts = token.lowercase(Locale.ROOT).split('x')
            if (parts.size == 2) {
                val w = parts[0].toIntOrNull() ?: 0
                val h = parts[1].toIntOrNull() ?: 0
                val edge = minOf(w, h)
                if (edge > maxEdge) maxEdge = edge
            }
        }
        return maxEdge
    }

    private fun JsonObject.optString(key: String): String? = this[key]?.optStringOrNull()

    private fun JsonElement.optStringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
