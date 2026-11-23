package app.overbay.swiftfox.features.reader

import android.content.Context
import kotlinx.serialization.Serializable
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
        val excerpt: String? = null,
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

    private fun readAsset(assetPath: String): String {
        val appContext = context?.applicationContext ?: context ?: return ""
        return runCatching {
            appContext.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
    }

    /**
     * Returns JavaScript that evaluates to boolean `true` or `false` inside `WebView.evaluateJavascript`.
     */
    fun buildCheckReadableScript(): String = buildString {
        append("(function(){")
        if (bridgeJs.isNotEmpty()) {
            append("if(typeof window.__swiftfoxIsReadable!=='function'){")
            append(bridgeJs)
            append('\n')
            append("}")
        }
        append("return typeof window.__swiftfoxIsReadable==='function'&&Boolean(window.__swiftfoxIsReadable());")
        append("})();")
    }

    /**
     * Returns JavaScript that lazily injects Readability + bridge if undefined and extracts the article JSON.
     */
    fun buildExtractScript(): String = buildString {
        append("(function(){")
        if (readabilityJs.isNotEmpty()) {
            append("if(typeof window.Readability!=='function'){")
            append(readabilityJs)
            append('\n')
            append("if(typeof Readability==='function'){window.Readability=Readability;}")
            append("}")
        }
        if (bridgeJs.isNotEmpty()) {
            append("if(typeof window.__swiftfoxExtractArticle!=='function'){")
            append(bridgeJs)
            append('\n')
            append("}")
        }
        append("return typeof window.__swiftfoxExtractArticle==='function'?window.__swiftfoxExtractArticle():null;")
        append("})();")
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

        val unwrappedJson = if (trimmed.startsWith("\"")) {
            runCatching { json.decodeFromString<String>(trimmed) }.getOrNull() ?: return null
        } else {
            trimmed
        }

        val payloadJson = unwrappedJson.trim()
        if (payloadJson.isEmpty() || payloadJson == "null" || !payloadJson.startsWith("{")) {
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

        val resolvedSiteName = payload.siteName?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedTitle = payload.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolvedSiteName
            ?: "Article"

        return ReaderArticle(
            url = payload.url?.trim().orEmpty(),
            title = resolvedTitle,
            byline = payload.byline?.trim()?.takeIf { it.isNotEmpty() },
            siteName = resolvedSiteName,
            excerpt = payload.excerpt?.trim()?.takeIf { it.isNotEmpty() },
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
                is ReaderBlock.Divider -> ""
            }
            if (text.isNotBlank()) {
                total += text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            }
        }
        return total
    }
}
