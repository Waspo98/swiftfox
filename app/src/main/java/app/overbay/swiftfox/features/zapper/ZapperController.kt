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

    fun buildStartScript(
        accentHex: String,
        bridgeToken: String = "",
        runtimeKey: String = DEFAULT_RUNTIME_KEY
    ): String {
        val safeHex = normalizeAccentHex(accentHex)
        val keyLit = jsStringLiteral(runtimeKey)
        val tokenLit = jsStringLiteral(bridgeToken)
        return buildString {
            if (zapperScript.isNotBlank()) {
                append("if (!Object.prototype.hasOwnProperty.call(window, ").append(keyLit).append(")) {\n")
                append(zapperScript)
                append("(").append(keyLit).append(", ").append(tokenLit).append(");\n")
                append("}\n")
            }
            append("var rt = window[").append(keyLit).append("];\n")
            append("rt && rt.start(").append(jsStringLiteral(safeHex)).append(");")
        }
    }

    fun buildStopScript(runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = jsStringLiteral(runtimeKey)
        return "var rt = window[$keyLit]; rt && rt.stop();"
    }

    fun buildSetScopeScript(depth: Int, runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = jsStringLiteral(runtimeKey)
        return "var rt = window[$keyLit]; rt && rt.setScopeDepth(${depth.coerceAtLeast(0)});"
    }

    fun buildAdjustScopeScript(delta: Int, runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = jsStringLiteral(runtimeKey)
        return "var rt = window[$keyLit]; rt && rt.adjustScope($delta);"
    }

    fun buildZapOnceScript(runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = jsStringLiteral(runtimeKey)
        return "var rt = window[$keyLit]; rt && rt.zapOnce();"
    }

    fun buildUndoLastZapScript(runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = jsStringLiteral(runtimeKey)
        return "var rt = window[$keyLit]; rt && rt.undoLastZap();"
    }

    fun buildConfirmBlockPermanentScript(runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = jsStringLiteral(runtimeKey)
        return "var rt = window[$keyLit]; rt && rt.confirmBlockPermanently();"
    }

    fun buildApplyZappedCssScript(rules: List<ZappedRuleEntity>): String {
        val css = buildCssForRules(rules)
        val cssLiteral = jsStringLiteral(css)
        return """
            (function() {
                var css = $cssLiteral;
                function attach() {
                    var id = 'swiftfox-zapped';
                    var style = document.getElementById(id);
                    if (!css) {
                        if (style) style.textContent = '';
                        return true;
                    }
                    if (!style) {
                        var target = document.documentElement || document.head || document.body;
                        if (!target) return false;
                        style = document.createElement('style');
                        style.id = id;
                        style.type = 'text/css';
                        target.appendChild(style);
                    } else if (document.documentElement && style.parentNode !== document.documentElement) {
                        document.documentElement.appendChild(style);
                    }
                    style.textContent = css;
                    return true;
                }
                if (!attach() && typeof document.addEventListener === 'function') {
                    document.addEventListener('DOMContentLoaded', attach, { once: true });
                }
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
                matchCount = event.matchCount.coerceAtLeast(0),
                viewportCenterY = event.viewportCenterY.coerceIn(0f, 1f)
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
        if (!HEX_COLOR_REGEX.matches(trimmed)) return DEFAULT_ACCENT_HEX
        return if (trimmed.length == 9) {
            // Strip ARGB alpha byte so CSS hex stays 6-digit #RRGGBB
            "#${trimmed.substring(3)}"
        } else {
            trimmed
        }
    }

    internal fun sanitizeSelector(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_SELECTOR_LENGTH) return null
        if (trimmed.startsWith("@")) return null
        val lower = trimmed.lowercase(java.util.Locale.ROOT)
        if (lower in UNSAFE_BARE_SELECTORS) return null
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
        // Validate balanced quotes/brackets/parens and reject comma-separated unsafe segments (e.g. "body, .ad")
        var inSingle = false
        var inDouble = false
        var escaped = false
        var bracketDepth = 0
        var parenDepth = 0
        val currentSeg = StringBuilder()
        for (ch in trimmed) {
            if (escaped) {
                currentSeg.append(ch)
                escaped = false
                continue
            }
            if (ch == '\\') {
                currentSeg.append(ch)
                escaped = true
                continue
            }
            if (inSingle) {
                if (ch == '\'') inSingle = false
                currentSeg.append(ch)
                continue
            }
            if (inDouble) {
                if (ch == '"') inDouble = false
                currentSeg.append(ch)
                continue
            }
            when (ch) {
                '\'' -> {
                    inSingle = true
                    currentSeg.append(ch)
                }
                '"' -> {
                    inDouble = true
                    currentSeg.append(ch)
                }
                '[' -> {
                    bracketDepth++
                    currentSeg.append(ch)
                }
                ']' -> {
                    if (bracketDepth == 0) return null
                    bracketDepth--
                    currentSeg.append(ch)
                }
                '(' -> {
                    parenDepth++
                    currentSeg.append(ch)
                }
                ')' -> {
                    if (parenDepth == 0) return null
                    parenDepth--
                    currentSeg.append(ch)
                }
                ',' -> {
                    if (bracketDepth == 0 && parenDepth == 0) {
                        val seg = currentSeg.toString().trim().lowercase(java.util.Locale.ROOT)
                        if (seg.isEmpty() || seg in UNSAFE_BARE_SELECTORS) return null
                        currentSeg.setLength(0)
                    } else {
                        currentSeg.append(ch)
                    }
                }
                else -> currentSeg.append(ch)
            }
        }
        if (escaped || inSingle || inDouble || bracketDepth != 0 || parenDepth != 0) return null
        val lastSeg = currentSeg.toString().trim().lowercase(java.util.Locale.ROOT)
        if (lastSeg.isEmpty() || lastSeg in UNSAFE_BARE_SELECTORS) return null
        return trimmed
    }

    companion object {
        const val DEFAULT_RUNTIME_KEY = "__swiftfoxZapper"
        private const val ZAPPER_ASSET_PATH = "js/zapper.js"
        private const val DEFAULT_ACCENT_HEX = "#E86A17"
        private const val MAX_SELECTOR_LENGTH = 1024
        private val HEX_COLOR_REGEX = Regex("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
        private val UNSAFE_BARE_SELECTORS = setOf(
            "html", "body", "head", "div", "span", "p", "a",
            "main", "section", "article", "ul", "ol", "li",
            "table", "tr", "td", "form", "iframe", "img", "svg",
            "button", "input", ":root", "*"
        )
    }
}
