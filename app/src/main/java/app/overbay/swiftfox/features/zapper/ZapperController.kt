package app.overbay.swiftfox.features.zapper

import android.content.Context
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Coordinates the Element Zapper DOM script, CSS rule serialization, and bridge JSON parsing.
 */
class ZapperController(private val context: Context? = null) {

    private val zapperScript: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val appContext = context?.applicationContext ?: context ?: return@lazy ""
        runCatching {
            appContext.assets.open(ZAPPER_ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun buildStartScript(accentHex: String): String {
        val safeHex = normalizeAccentHex(accentHex)
        return buildString {
            if (zapperScript.isNotBlank()) {
                append(zapperScript)
                append('\n')
            }
            append("window.__swiftfoxZapper && window.__swiftfoxZapper.start(")
            append(jsStringLiteral(safeHex))
            append(");")
        }
    }

    fun buildStopScript(): String =
        "window.__swiftfoxZapper && window.__swiftfoxZapper.stop();"

    fun buildSetScopeScript(depth: Int): String =
        "window.__swiftfoxZapper && window.__swiftfoxZapper.setScopeDepth(${depth.coerceAtLeast(0)});"

    fun buildAdjustScopeScript(delta: Int): String =
        "window.__swiftfoxZapper && window.__swiftfoxZapper.adjustScope($delta);"

    fun buildZapOnceScript(): String =
        "window.__swiftfoxZapper && window.__swiftfoxZapper.zapOnce();"

    fun buildUndoLastZapScript(): String =
        "window.__swiftfoxZapper && window.__swiftfoxZapper.undoLastZap();"

    fun buildConfirmBlockPermanentScript(): String =
        "window.__swiftfoxZapper && window.__swiftfoxZapper.confirmBlockPermanently();"

    fun buildApplyZappedCssScript(rules: List<ZappedRuleEntity>): String {
        val css = buildCssForRules(rules)
        val cssLiteral = jsStringLiteral(css)
        return """
            (function() {
                var css = $cssLiteral;
                if (window.__swiftfoxApplyZappedCss) {
                    window.__swiftfoxApplyZappedCss(css);
                    return;
                }
                var id = 'swiftfox-zapped';
                var style = document.getElementById(id);
                if (!css) {
                    if (style) style.textContent = '';
                    return;
                }
                if (!style) {
                    style = document.createElement('style');
                    style.id = id;
                    style.type = 'text/css';
                    var target = document.head || document.documentElement;
                    if (target) target.appendChild(style);
                }
                style.textContent = css;
            })();
        """.trimIndent()
    }

    fun buildCssForRules(rules: List<ZappedRuleEntity>): String =
        rules.asSequence()
            .filter { it.enabled }
            .mapNotNull { sanitizeSelector(it.selector) }
            .distinct()
            .joinToString("\n") { safeSelector ->
                "$safeSelector { display: none !important; }"
            }

    fun parseSelectionEvent(json: String): ZapperSelectionEvent? {
        if (json.isBlank()) return null
        return runCatching {
            val event = jsonParser.decodeFromString<ZapperSelectionEvent>(json)
            val cleanSelector = sanitizeSelector(event.selector) ?: return@runCatching null
            val maxDepth = event.maxScopeDepth.coerceAtLeast(0)
            event.copy(
                selector = cleanSelector,
                scopeDepth = event.scopeDepth.coerceIn(0, maxDepth),
                maxScopeDepth = maxDepth,
                matchCount = event.matchCount.coerceAtLeast(0)
            )
        }.getOrNull()
    }

    fun parseTemporaryZapEvent(json: String): ZapperTemporaryZapEvent? {
        if (json.isBlank()) return null
        return runCatching {
            val event = jsonParser.decodeFromString<ZapperTemporaryZapEvent>(json)
            event.copy(temporaryZapCount = event.temporaryZapCount.coerceAtLeast(0))
        }.getOrNull()
    }

    fun parsePermanentRuleEvent(json: String): ZapperPermanentRuleEvent? {
        if (json.isBlank()) return null
        return runCatching {
            val event = jsonParser.decodeFromString<ZapperPermanentRuleEvent>(json)
            val cleanSelector = sanitizeSelector(event.selector) ?: return@runCatching null
            event.copy(
                selector = cleanSelector,
                label = event.label.trim(),
                matchCount = event.matchCount.coerceAtLeast(0)
            )
        }.getOrNull()
    }

    fun parseUndoCount(json: String): Int {
        if (json.isBlank()) return 0
        return runCatching {
            jsonParser.decodeFromString<ZapperUndoEvent>(json)
                .temporaryZapCount
                .coerceAtLeast(0)
        }.getOrDefault(0)
    }

    private fun jsStringLiteral(value: String): String =
        jsonParser.encodeToString(value)
            .replace("<", "\\u003c")
            .replace(">", "\\u003e")

    private fun normalizeAccentHex(accentHex: String): String {
        val trimmed = accentHex.trim()
        return if (HEX_COLOR_REGEX.matches(trimmed)) trimmed else DEFAULT_ACCENT_HEX
    }

    internal fun sanitizeSelector(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_SELECTOR_LENGTH) return null
        if (trimmed.startsWith("@")) return null
        val lower = trimmed.lowercase()
        if (lower.contains("</style") ||
            trimmed.contains('<') ||
            trimmed.contains('{') ||
            trimmed.contains('}') ||
            trimmed.contains(';') ||
            trimmed.contains("/*") ||
            trimmed.contains("*/") ||
            trimmed.any { it == '\n' || it == '\r' || it.code < 0x20 }
        ) {
            return null
        }
        return trimmed
    }

    private companion object {
        const val ZAPPER_ASSET_PATH = "js/zapper.js"
        const val DEFAULT_ACCENT_HEX = "#E86A17"
        const val MAX_SELECTOR_LENGTH = 1024
        val HEX_COLOR_REGEX = Regex("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    }
}
