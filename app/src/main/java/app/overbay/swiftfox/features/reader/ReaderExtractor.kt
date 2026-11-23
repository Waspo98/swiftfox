package app.overbay.swiftfox.features.reader

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Coordinates Readability JS injection and extraction payload parsing for WebView pages.
 */
class ReaderExtractor(private val context: Context?) {

    @Serializable
    private data class RawExtractionPayload(
        val title: String? = null,
        val byline: String? = null,
        val siteName: String? = null,
        val htmlContent: String? = null,
        val wordCount: Int? = null,
        val readingTimeMinutes: Int? = null,
        val url: String? = null
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val readabilityJs: String by lazy {
        readAsset("js/readability.min.js")
    }

    private val bridgeJs: String by lazy {
        readAsset("js/readability_bridge.js")
    }

    fun warmUpAssets() {
        readabilityJs
        bridgeJs
    }

    private fun readAsset(assetPath: String): String {
        val appContext = context?.applicationContext ?: context ?: return ""
        return runCatching {
            appContext.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
    }

    /**
     * Returns JavaScript that evaluates to boolean `true` or `false` inside `WebView.evaluateJavascript`
     * without consulting spoofable page globals.
     */
    fun buildCheckReadableScript(): String = buildString {
        append("(function(){")
        if (bridgeJs.isNotEmpty()) {
            append("var rt=(").append(bridgeJs).append(")(null);")
            append("return Boolean(rt&&rt.isReadable());")
        } else {
            append("return false;")
        }
        append("})();")
    }

    /**
     * Fast-path probe script used when Readability + bridge are already attached under [runtimeKey].
     */
    fun buildFastExtractScript(runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = json.encodeToString(runtimeKey)
        return "(function(){var rt=window[$keyLit];return rt&&typeof rt.extractArticle==='function'?rt.extractArticle():null;})();"
    }

    /**
     * Returns JavaScript that lazily injects Readability (isolated from host page UMD globals)
     * + bridge under an unguessable [runtimeKey] and extracts the article JSON.
     */
    fun buildExtractScript(runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = json.encodeToString(runtimeKey)
        return buildString {
            append("(function(){")
            if (readabilityJs.isNotEmpty() && bridgeJs.isNotEmpty()) {
                append("if(!Object.prototype.hasOwnProperty.call(window,").append(keyLit).append(")){")
                append("var define=undefined,exports=undefined,module=undefined;")
                append(readabilityJs)
                append('\n')
                append("var __ctor=typeof Readability==='function'?Readability:null;")
                append("var __rt=(").append(bridgeJs).append(")(__ctor);")
                append("try{Object.defineProperty(window,").append(keyLit).append(",{value:__rt,writable:false,configurable:false,enumerable:false});}catch(e){window[").append(keyLit).append("]=__rt;}")
                append("}")
            }
            append("var rt=window[").append(keyLit).append("];")
            append("return rt&&typeof rt.extractArticle==='function'?rt.extractArticle():null;")
            append("})();")
        }
    }

    /**
     * Parses the raw string passed to `WebView.evaluateJavascript`'s callback into a [ReaderArticle].
     * Unwraps the outer JSON string literal produced by Chromium when JS returns a string.
     */
    fun parseExtractionResult(rawJsResult: String?): ReaderArticle? {
        val trimmed = rawJsResult?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "undefined") {
            return null
        }
        // Guard before decoding the outer JSON string literal to avoid unbounded allocation
        if (trimmed.length > MAX_EXTRACTION_CHARS * 2) {
            return null
        }

        val unwrappedJson = if (trimmed.startsWith("\"")) {
            runCatching { json.decodeFromString<String>(trimmed) }.getOrNull() ?: return null
        } else {
            trimmed
        }

        val payloadJson = unwrappedJson.trim()
        if (payloadJson.isEmpty() || payloadJson == "null" || !payloadJson.startsWith("{")) {
            return null
        }
        if (payloadJson.length > MAX_EXTRACTION_CHARS) {
            return null
        }

        val payload = runCatching {
            json.decodeFromString<RawExtractionPayload>(payloadJson)
        }.getOrNull() ?: return null

        val htmlContent = payload.htmlContent?.takeIf { it.isNotBlank() } ?: return null
        val blocks = ReaderHtmlParser.parse(htmlContent)
        if (blocks.isEmpty()) return null

        val wordCount = (payload.wordCount ?: estimateWordCount(blocks)).coerceAtLeast(0)
        val readingTimeMinutes = (payload.readingTimeMinutes ?: (wordCount / 220f).roundToInt())
            .coerceAtLeast(1)

        val resolvedSiteName = payload.siteName?.replace(WHITESPACE_REGEX, " ")?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedTitle = payload.title?.replace(WHITESPACE_REGEX, " ")?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolvedSiteName
            ?: "Article"
        val resolvedByline = payload.byline?.replace(WHITESPACE_REGEX, " ")?.trim()?.takeIf { it.isNotEmpty() }

        return ReaderArticle(
            url = payload.url?.trim().orEmpty(),
            title = resolvedTitle,
            byline = resolvedByline,
            siteName = resolvedSiteName,
            blocks = blocks,
            wordCount = wordCount,
            readingTimeMinutes = readingTimeMinutes
        )
    }

    private fun estimateWordCount(blocks: List<ReaderBlock>): Int {
        var total = 0
        for (block in blocks) {
            val text = when (block) {
                is ReaderBlock.Heading -> block.text
                is ReaderBlock.Paragraph -> block.runs.joinToString("") { it.text }
                is ReaderBlock.Quote -> block.text
                is ReaderBlock.CodeBlock -> block.code
                is ReaderBlock.BulletItem -> block.runs.joinToString("") { it.text }
                is ReaderBlock.Image -> listOfNotNull(block.caption, block.alt).joinToString(" ")
                is ReaderBlock.Table -> buildString {
                    block.caption?.let { append(it).append(' ') }
                    block.headers.forEach { cell ->
                        append(cell.joinToString("") { it.text }).append(' ')
                    }
                    block.rows.forEach { row ->
                        row.forEach { cell ->
                            append(cell.joinToString("") { it.text }).append(' ')
                        }
                    }
                }
                is ReaderBlock.DefinitionItem -> buildString {
                    append(block.term.joinToString("") { it.text }).append(' ')
                    block.definitions.forEach { def ->
                        append(def.joinToString("") { it.text }).append(' ')
                    }
                }
                is ReaderBlock.Divider -> ""
            }
            if (text.isNotBlank()) {
                total += text.trim().split(WHITESPACE_REGEX).count { it.isNotEmpty() }
            }
        }
        return total
    }

    companion object {
        const val DEFAULT_RUNTIME_KEY = "__swiftfoxReader"
        private const val MAX_EXTRACTION_CHARS = 2 * 1024 * 1024
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
