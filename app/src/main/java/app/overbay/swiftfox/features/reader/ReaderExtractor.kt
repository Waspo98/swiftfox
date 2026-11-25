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

    private val extractBootstrapFactory: String by lazy {
        if (readabilityJs.isEmpty() || bridgeJs.isEmpty()) {
            ""
        } else {
            buildString(readabilityJs.length + bridgeJs.length + 320) {
                append("function(rk){")
                append("var __rdExp={},__rdMod={exports:__rdExp};")
                append("(function(define,module,exports){")
                append(readabilityJs)
                append("\n}).call(globalThis,undefined,__rdMod,__rdExp);")
                append("var __ctor=typeof __rdMod.exports==='function'?__rdMod.exports:(__rdMod.exports&&typeof __rdMod.exports.Readability==='function'?__rdMod.exports.Readability:(typeof Readability==='function'?Readability:null));")
                append("var __rt=(").append(bridgeJs).append(")(__ctor);")
                append("try{Object.defineProperty(window,rk,{value:__rt,writable:false,configurable:false,enumerable:false});}catch(e){window[rk]=__rt;}")
                append("}")
            }
        }
    }

    fun warmUpAssets() {
        readabilityJs
        bridgeJs
        extractBootstrapFactory
    }

    private fun readAsset(assetPath: String): String {
        val appContext = context?.applicationContext ?: context ?: return ""
        return runCatching {
            appContext.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
    }

    /**
     * Lightweight ~1.3 KB readability heuristic script that evaluates to boolean `true` or `false`
     * with early exit after 2 paragraphs (>=420 chars), avoiding loading Readability or cloning DOM
     * on normal page loads.
     */
    fun buildCheckReadableScript(): String = CHECK_READABLE_SCRIPT

    /**
     * Fast-path probe script used when Readability + bridge are already attached under [runtimeKey].
     */
    fun buildFastExtractScript(runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = json.encodeToString(runtimeKey)
        return "(function(){var rt=window[$keyLit];return rt&&typeof rt.extractArticle==='function'?rt.extractArticle():null;})();"
    }

    /**
     * Returns JavaScript that lazily injects Readability (isolated inside a CommonJS closure so
     * `window.Readability` never leaks onto host pages) + bridge under an unguessable [runtimeKey].
     */
    fun buildExtractScript(runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = json.encodeToString(runtimeKey)
        val factory = extractBootstrapFactory
        return buildString(factory.length + keyLit.length * 3 + 180) {
            append("(function(){")
            if (factory.isNotEmpty()) {
                append("if(!Object.prototype.hasOwnProperty.call(window,").append(keyLit).append(")){")
                append("(").append(factory).append(")(").append(keyLit).append(");")
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
        val articleUrl = payload.url?.trim().orEmpty()
        val blocks = ReaderHtmlParser.parse(htmlContent, baseUrl = articleUrl.ifEmpty { null })
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
            url = articleUrl,
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
            when (block) {
                is ReaderBlock.Heading -> total += countWordsInString(block.text)
                is ReaderBlock.Paragraph -> total += countWordsInRuns(block.runs)
                is ReaderBlock.Quote -> total += countWordsInString(block.text)
                is ReaderBlock.CodeBlock -> total += countWordsInString(block.code)
                is ReaderBlock.BulletItem -> total += countWordsInRuns(block.runs)
                is ReaderBlock.Image -> {
                    block.caption?.let { total += countWordsInString(it) }
                    block.alt?.let { total += countWordsInString(it) }
                }
                is ReaderBlock.Table -> {
                    block.caption?.let { total += countWordsInString(it) }
                    block.headers.forEach { total += countWordsInRuns(it) }
                    block.rows.forEach { row -> row.forEach { total += countWordsInRuns(it) } }
                }
                is ReaderBlock.DefinitionItem -> {
                    total += countWordsInRuns(block.term)
                    block.definitions.forEach { total += countWordsInRuns(it) }
                }
                is ReaderBlock.Divider -> Unit
            }
        }
        return total
    }

    private fun countWordsInString(s: String): Int {
        var count = 0
        var inWord = false
        for (i in s.indices) {
            if (s[i].isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
    }

    private fun countWordsInRuns(runs: List<TextRun>): Int {
        var count = 0
        var inWord = false
        for (run in runs) {
            val s = run.text
            for (i in s.indices) {
                if (s[i].isWhitespace()) {
                    inWord = false
                } else if (!inWord) {
                    inWord = true
                    count++
                }
            }
        }
        return count
    }

    companion object {
        const val DEFAULT_RUNTIME_KEY = "__swiftfoxReader"
        private const val MAX_EXTRACTION_CHARS = 2 * 1024 * 1024
        private val WHITESPACE_REGEX = Regex("\\s+")

        private const val CHECK_READABLE_SCRIPT =
            "(function(){try{if(!document||!document.body)return false;" +
                "var pn=(window.location&&window.location.pathname)?window.location.pathname.toLowerCase():'';" +
                "if(/(?:^|\\/)(login|signin|sign-in|signup|sign-up|register|auth|oauth|sso|checkout|cart|search|settings|preferences|forgot-password|reset-password)(?:\\/|$)/.test(pn))return false;" +
                "var hasPw=false,pws=document.querySelectorAll('input[type=\"password\"]');" +
                "for(var k=0;k<pws.length;k++){var inp=pws[k];if(inp.offsetWidth>0||inp.offsetHeight>0||(inp.getClientRects&&inp.getClientRects().length>0)){hasPw=true;break;}}" +
                "var minP=hasPw?800:420,ps=document.querySelectorAll('p'),pLen=0,pCnt=0;" +
                "for(var i=0;i<ps.length;i++){var p=ps[i];if(p.closest){if(p.closest('nav, footer'))continue;var f=p.closest('form');if(f&&f.querySelector('input[type=\"password\"], input[type=\"email\"]'))continue;}" +
                "var t=(p.textContent||'').trim();if(t.length>0){pLen+=t.length;pCnt++;if(pCnt>=2&&pLen>=minP)return true;}}" +
                "var minC=hasPw?800:600,cs=document.querySelectorAll('article, main'),maxC=0,artLen=0;" +
                "for(var j=0;j<cs.length;j++){var el=cs[j],et=(el.textContent||'').trim();if(et.length>maxC){maxC=et.length;if(maxC>=minC)return true;}" +
                "if(el.tagName==='ARTICLE'&&!(el.parentElement&&el.parentElement.closest&&el.parentElement.closest('article')))artLen+=et.length;}" +
                "if(hasPw&&pLen<800&&maxC<800)return false;" +
                "return((pLen>=420||artLen>=420)&&pCnt>=2)||maxC>=600;}catch(e){return false;}})();"
    }
}
