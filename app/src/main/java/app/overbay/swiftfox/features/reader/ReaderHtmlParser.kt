package app.overbay.swiftfox.features.reader

import java.net.URI

/**
 * Pure-Kotlin HTML-to-[ReaderBlock] parser for Readability output and plain-text fallbacks.
 * Supports headings, paragraphs, blockquotes, code blocks, nested lists, tables, images/figures,
 * definition lists, inline formatting (bold, italic, inline code, strikethrough, safe HTTP(S) links),
 * HTML5 entities, and resilient raw-tag / CDATA handling without `android.text.Html`.
 */
object ReaderHtmlParser {

    private const val MAX_HTML_CHARS = 2_000_000
    private const val MAX_DOM_DEPTH = 64

    private val activeParseBaseUri = ThreadLocal<URI?>()

    private sealed interface HtmlToken {
        data class Text(val raw: String, val decodeEntities: Boolean = true) : HtmlToken
        data class StartTag(
            val name: String,
            val attrs: Map<String, String>,
            val selfClosing: Boolean
        ) : HtmlToken
        data class EndTag(val name: String) : HtmlToken
    }

    private sealed interface HtmlNode {
        data class TextNode(val raw: String, val decodeEntities: Boolean = true) : HtmlNode
        data class ElementNode(
            val name: String,
            val attrs: Map<String, String>,
            val children: MutableList<HtmlNode> = mutableListOf()
        ) : HtmlNode
    }

    private data class InlineStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strikethrough: Boolean = false,
        val linkUrl: String? = null
    )

    private val VOID_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    private val IGNORED_RAW_TAGS = setOf("script", "style", "noscript", "template", "svg", "head")
    private val NESTABLE_RAW_TAGS = setOf("svg", "template")

    private val BLOCK_TAGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6",
        "p", "blockquote", "pre", "ul", "ol", "li", "hr",
        "div", "section", "article", "main", "header", "footer", "aside",
        "figure", "figcaption", "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption",
        "dl", "dt", "dd"
    )

    private val AUTO_CLOSE_P_TAGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6",
        "p", "blockquote", "pre", "ul", "ol", "hr",
        "div", "section", "article", "main", "table", "figure", "dl"
    )

    private val ENTITY_REGEX = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z][a-zA-Z0-9]*);")
    private val BLANK_LINE_REGEX = Regex("\\r?\\n\\s*\\r?\\n")

    private val NAMED_ENTITIES = mapOf(
        // Core XML & spacing
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "ensp" to " ",
        "emsp" to " ",
        "thinsp" to " ",
        "shy" to "",
        "zwnj" to "\u200C",
        "zwj" to "\u200D",
        // Punctuation & quotes
        "mdash" to "\u2014",
        "ndash" to "\u2013",
        "hellip" to "\u2026",
        "lsquo" to "\u2018",
        "rsquo" to "\u2019",
        "sbquo" to "\u201A",
        "ldquo" to "\u201C",
        "rdquo" to "\u201D",
        "bdquo" to "\u201E",
        "laquo" to "\u00AB",
        "raquo" to "\u00BB",
        "lsaquo" to "\u2039",
        "rsaquo" to "\u203A",
        "bull" to "\u2022",
        "middot" to "\u00B7",
        "sect" to "\u00A7",
        "para" to "\u00B6",
        "dagger" to "\u2020",
        "Dagger" to "\u2021",
        "permil" to "\u2030",
        "prime" to "\u2032",
        "Prime" to "\u2033",
        "iexcl" to "\u00A1",
        "iquest" to "\u00BF",
        "brvbar" to "\u00A6",
        "ordf" to "\u00AA",
        "ordm" to "\u00BA",
        "not" to "\u00AC",
        // Legal, currency, math & units
        "copy" to "\u00A9",
        "reg" to "\u00AE",
        "trade" to "\u2122",
        "deg" to "\u00B0",
        "euro" to "\u20AC",
        "pound" to "\u00A3",
        "yen" to "\u00A5",
        "cent" to "\u00A2",
        "curren" to "\u00A4",
        "times" to "\u00D7",
        "divide" to "\u00F7",
        "plusmn" to "\u00B1",
        "minus" to "\u2212",
        "sup1" to "\u00B9",
        "sup2" to "\u00B2",
        "sup3" to "\u00B3",
        "frac14" to "\u00BC",
        "frac12" to "\u00BD",
        "frac34" to "\u00BE",
        "micro" to "\u00B5",
        "le" to "\u2264",
        "ge" to "\u2265",
        "ne" to "\u2260",
        "asymp" to "\u2248",
        "equiv" to "\u2261",
        "infin" to "\u221E",
        "radic" to "\u221A",
        "sum" to "\u2211",
        "prod" to "\u220F",
        "int" to "\u222B",
        "part" to "\u2202",
        "empty" to "\u2205",
        "nabla" to "\u2207",
        "isin" to "\u2208",
        "notin" to "\u2209",
        "cap" to "\u2229",
        "cup" to "\u222A",
        "sub" to "\u2282",
        "sup" to "\u2283",
        "and" to "\u2227",
        "or" to "\u2228",
        "there4" to "\u2234",
        "sim" to "\u223C",
        "cong" to "\u2245",
        "propto" to "\u221D",
        "ang" to "\u2220",
        "perp" to "\u22A5",
        "sdot" to "\u22C5",
        // Arrows
        "larr" to "\u2190",
        "uarr" to "\u2191",
        "rarr" to "\u2192",
        "darr" to "\u2193",
        "harr" to "\u2194",
        "lArr" to "\u21D0",
        "uArr" to "\u21D1",
        "rArr" to "\u21D2",
        "dArr" to "\u21D3",
        "hArr" to "\u21D4",
        // Full Greek alphabet (case-sensitive)
        "alpha" to "\u03B1",
        "beta" to "\u03B2",
        "gamma" to "\u03B3",
        "delta" to "\u03B4",
        "epsilon" to "\u03B5",
        "zeta" to "\u03B6",
        "eta" to "\u03B7",
        "theta" to "\u03B8",
        "iota" to "\u03B9",
        "kappa" to "\u03BA",
        "lambda" to "\u03BB",
        "mu" to "\u03BC",
        "nu" to "\u03BD",
        "xi" to "\u03BE",
        "omicron" to "\u03BF",
        "pi" to "\u03C0",
        "rho" to "\u03C1",
        "sigmaf" to "\u03C2",
        "sigma" to "\u03C3",
        "tau" to "\u03C4",
        "upsilon" to "\u03C5",
        "phi" to "\u03C6",
        "chi" to "\u03C7",
        "psi" to "\u03C8",
        "omega" to "\u03C9",
        "Alpha" to "\u0391",
        "Beta" to "\u0392",
        "Gamma" to "\u0393",
        "Delta" to "\u0394",
        "Epsilon" to "\u0395",
        "Zeta" to "\u0396",
        "Eta" to "\u0397",
        "Theta" to "\u0398",
        "Iota" to "\u0399",
        "Kappa" to "\u039A",
        "Lambda" to "\u039B",
        "Mu" to "\u039C",
        "Nu" to "\u039D",
        "Xi" to "\u039E",
        "Omicron" to "\u039F",
        "Pi" to "\u03A0",
        "Rho" to "\u03A1",
        "Sigma" to "\u03A3",
        "Tau" to "\u03A4",
        "Upsilon" to "\u03A5",
        "Phi" to "\u03A6",
        "Chi" to "\u03A7",
        "Psi" to "\u03A8",
        "Omega" to "\u03A9",
        // Common Latin-1 / European diacritics
        "Agrave" to "\u00C0", "Aacute" to "\u00C1", "Acirc" to "\u00C2", "Atilde" to "\u00C3",
        "Auml" to "\u00C4", "Aring" to "\u00C5", "AElig" to "\u00C6", "Ccedil" to "\u00C7",
        "Egrave" to "\u00C8", "Eacute" to "\u00C9", "Ecirc" to "\u00CA", "Euml" to "\u00CB",
        "Igrave" to "\u00CC", "Iacute" to "\u00CD", "Icirc" to "\u00CE", "Iuml" to "\u00CF",
        "Ntilde" to "\u00D1", "Ograve" to "\u00D2", "Oacute" to "\u00D3", "Ocirc" to "\u00D4",
        "Otilde" to "\u00D5", "Ouml" to "\u00D6", "Oslash" to "\u00D8",
        "Ugrave" to "\u00D9", "Uacute" to "\u00DA", "Ucirc" to "\u00DB", "Uuml" to "\u00DC",
        "Yacute" to "\u00DD", "szlig" to "\u00DF",
        "agrave" to "\u00E0", "aacute" to "\u00E1", "acirc" to "\u00E2", "atilde" to "\u00E3",
        "auml" to "\u00E4", "aring" to "\u00E5", "aelig" to "\u00E6", "ccedil" to "\u00E7",
        "egrave" to "\u00E8", "eacute" to "\u00E9", "ecirc" to "\u00EA", "euml" to "\u00EB",
        "igrave" to "\u00EC", "iacute" to "\u00ED", "icirc" to "\u00EE", "iuml" to "\u00EF",
        "ntilde" to "\u00F1", "ograve" to "\u00F2", "oacute" to "\u00F3", "ocirc" to "\u00F4",
        "otilde" to "\u00F5", "ouml" to "\u00F6", "oslash" to "\u00F8",
        "ugrave" to "\u00F9", "uacute" to "\u00FA", "ucirc" to "\u00FB", "uuml" to "\u00FC",
        "yacute" to "\u00FD", "yuml" to "\u00FF",
        "OElig" to "\u0152", "oelig" to "\u0153", "Scaron" to "\u0160", "scaron" to "\u0161",
        "Yuml" to "\u0178"
    )

    fun parse(html: String, baseUrl: String? = null): List<ReaderBlock> {
        if (html.isBlank()) return emptyList()
        val bounded = if (html.length > MAX_HTML_CHARS) html.substring(0, MAX_HTML_CHARS) else html
        val parsedBaseUri = baseUrl?.trim()?.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }?.let { runCatching { URI(it) }.getOrNull() }
        val prevBase = activeParseBaseUri.get()
        if (parsedBaseUri != null) {
            activeParseBaseUri.set(parsedBaseUri)
        } else {
            activeParseBaseUri.remove()
        }
        try {
            val tokens = tokenize(bounded)
            val root = buildTree(tokens)
            val blocks = mutableListOf<ReaderBlock>()
            var nextId = 0

            fun generateId(prefix: String): String = "${prefix}_${nextId++}"

            emitChildrenBlocks(root.children, blocks, ::generateId, depth = 0)

            if (blocks.isEmpty()) {
                val fallbackText = extractPlainFallbackText(root)
                if (fallbackText.isNotBlank()) {
                    fallbackText.split(BLANK_LINE_REGEX).forEach { chunk ->
                        val cleaned = collapseWhitespace(chunk)
                        if (cleaned.isNotEmpty()) {
                            blocks.add(
                                ReaderBlock.Paragraph(
                                    id = generateId("p"),
                                    runs = listOf(TextRun(text = cleaned))
                                )
                            )
                        }
                    }
                }
            }

            return blocks
        } finally {
            if (prevBase != null) {
                activeParseBaseUri.set(prevBase)
            } else {
                activeParseBaseUri.remove()
            }
        }
    }

    internal fun sanitizeHttpUrl(raw: String?, baseUri: URI? = activeParseBaseUri.get()): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.any { it.code < 0x20 || it.code == 0x7F }) return null
        val lower = trimmed.lowercase(java.util.Locale.ROOT)
        if (lower.startsWith("javascript:") ||
            lower.startsWith("data:") ||
            lower.startsWith("vbscript:") ||
            lower.startsWith("file:") ||
            lower.startsWith("intent:") ||
            lower.startsWith("content:") ||
            lower.startsWith("blob:") ||
            lower.startsWith("mailto:") ||
            lower.startsWith("tel:") ||
            lower.startsWith("#")
        ) {
            return null
        }
        val candidate = if (!lower.startsWith("http://") && !lower.startsWith("https://") && baseUri != null) {
            runCatching { baseUri.resolve(trimmed).toString() }.getOrNull() ?: return null
        } else {
            trimmed
        }
        val candidateLower = candidate.lowercase(java.util.Locale.ROOT)
        if (!candidateLower.startsWith("http://") && !candidateLower.startsWith("https://")) return null
        val afterScheme = candidate.substringAfter("://")
        if (afterScheme.isEmpty() || afterScheme.startsWith("/")) return null
        return candidate
    }

    internal fun decodeEntities(input: String): String {
        if (!input.contains('&')) return input
        return ENTITY_REGEX.replace(input) { match ->
            val entity = match.groupValues[1]
            when {
                entity.startsWith("#x", ignoreCase = true) -> {
                    val codePoint = entity.substring(2).toIntOrNull(16)
                    codePointToString(codePoint) ?: match.value
                }
                entity.startsWith("#") -> {
                    val codePoint = entity.substring(1).toIntOrNull(10)
                    codePointToString(codePoint) ?: match.value
                }
                else -> NAMED_ENTITIES[entity] ?: match.value
            }
        }
    }

    private fun codePointToString(codePoint: Int?): String? {
        if (codePoint == null || codePoint <= 0 || codePoint > 0x10FFFF) return null
        if (codePoint in 0xD800..0xDFFF) return null
        // HTML5 maps legacy numeric entities 128..159 to Windows-1252 punctuation characters
        val mapped = WINDOWS_1252_MAP[codePoint] ?: codePoint
        return String(Character.toChars(mapped))
    }

    private val WINDOWS_1252_MAP = mapOf(
        0x80 to 0x20AC, 0x82 to 0x201A, 0x83 to 0x0192, 0x84 to 0x201E,
        0x85 to 0x2026, 0x86 to 0x2020, 0x87 to 0x2021, 0x88 to 0x02C6,
        0x89 to 0x2030, 0x8A to 0x0160, 0x8B to 0x2039, 0x8C to 0x0152,
        0x8E to 0x017D, 0x91 to 0x2018, 0x92 to 0x2019, 0x93 to 0x201C,
        0x94 to 0x201D, 0x95 to 0x2022, 0x96 to 0x2013, 0x97 to 0x2014,
        0x98 to 0x02DC, 0x99 to 0x2122, 0x9A to 0x0161, 0x9B to 0x203A,
        0x9C to 0x0153, 0x9E to 0x017E, 0x9F to 0x0178
    )

    private fun tokenize(html: String): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val len = html.length
        var i = 0

        while (i < len) {
            val lt = html.indexOf('<', i)
            if (lt == -1) {
                tokens.add(HtmlToken.Text(html.substring(i)))
                break
            }
            if (lt > i) {
                tokens.add(HtmlToken.Text(html.substring(i, lt)))
            }

            if (lt + 1 >= len) {
                tokens.add(HtmlToken.Text("<"))
                break
            }

            // HTML comment
            if (html.startsWith("<!--", lt)) {
                val endComment = html.indexOf("-->", lt + 4)
                i = if (endComment == -1) len else endComment + 3
                continue
            }

            // CDATA section
            if (html.startsWith("<![CDATA[", lt)) {
                val endCdata = html.indexOf("]]>", lt + 9)
                if (endCdata == -1) {
                    tokens.add(HtmlToken.Text(html.substring(lt + 9), decodeEntities = false))
                    i = len
                } else {
                    tokens.add(HtmlToken.Text(html.substring(lt + 9, endCdata), decodeEntities = false))
                    i = endCdata + 3
                }
                continue
            }

            // Declaration / processing instruction (e.g. <!DOCTYPE html>)
            val nextChar = html[lt + 1]
            if (nextChar == '!' || nextChar == '?') {
                val gt = html.indexOf('>', lt + 2)
                i = if (gt == -1) len else gt + 1
                continue
            }

            // End tag
            if (nextChar == '/') {
                val nameStart = lt + 2
                if (nameStart >= len || !isAsciiLetter(html[nameStart])) {
                    tokens.add(HtmlToken.Text("</"))
                    i = lt + 2
                    continue
                }
                var cursor = nameStart
                while (cursor < len && isTagNameChar(html[cursor])) cursor++
                val tagName = html.substring(nameStart, cursor).lowercase(java.util.Locale.ROOT)
                val gt = html.indexOf('>', cursor)
                i = if (gt == -1) len else gt + 1
                tokens.add(HtmlToken.EndTag(tagName))
                continue
            }

            // Start tag
            if (!isAsciiLetter(nextChar)) {
                tokens.add(HtmlToken.Text("<"))
                i = lt + 1
                continue
            }

            var cursor = lt + 1
            while (cursor < len && isTagNameChar(html[cursor])) cursor++
            val tagName = html.substring(lt + 1, cursor).lowercase(java.util.Locale.ROOT)

            val attrs = LinkedHashMap<String, String>()
            var selfClosing = false

            while (cursor < len) {
                while (cursor < len && html[cursor].isWhitespace()) cursor++
                if (cursor >= len) break
                val ch = html[cursor]
                if (ch == '>') {
                    cursor++
                    break
                }
                if (ch == '/' && cursor + 1 < len && html[cursor + 1] == '>') {
                    selfClosing = true
                    cursor += 2
                    break
                }
                if (ch == '/') {
                    cursor++
                    continue
                }

                // Attribute name
                val attrStart = cursor
                while (cursor < len && !html[cursor].isWhitespace() &&
                    html[cursor] != '=' && html[cursor] != '>' && html[cursor] != '/'
                ) {
                    cursor++
                }
                val attrName = html.substring(attrStart, cursor).lowercase(java.util.Locale.ROOT)
                while (cursor < len && html[cursor].isWhitespace()) cursor++

                var attrVal = ""
                if (cursor < len && html[cursor] == '=') {
                    cursor++
                    while (cursor < len && html[cursor].isWhitespace()) cursor++
                    if (cursor < len) {
                        val quote = html[cursor]
                        if (quote == '"' || quote == '\'') {
                            cursor++
                            val valStart = cursor
                            while (cursor < len && html[cursor] != quote) cursor++
                            attrVal = decodeEntities(html.substring(valStart, cursor))
                            if (cursor < len && html[cursor] == quote) cursor++
                        } else {
                            val valStart = cursor
                            while (cursor < len && !html[cursor].isWhitespace() && html[cursor] != '>') {
                                if (html[cursor] == '/' && cursor + 1 < len && html[cursor + 1] == '>') {
                                    break
                                }
                                cursor++
                            }
                            attrVal = decodeEntities(html.substring(valStart, cursor))
                        }
                    }
                }
                if (attrName.isNotEmpty()) {
                    attrs[attrName] = attrVal
                }
            }

            tokens.add(HtmlToken.StartTag(tagName, attrs, selfClosing))
            i = cursor

            // Skip raw contents of script/style/noscript/template/svg tags accurately,
            // allowing whitespace before `>` and tracking nesting depth for svg/template.
            if (!selfClosing && tagName in IGNORED_RAW_TAGS && tagName != "head") {
                val afterClose = skipRawTagBody(html, i, tagName)
                tokens.add(HtmlToken.EndTag(tagName))
                i = afterClose
            }
        }

        return tokens
    }

    private fun skipRawTagBody(html: String, startIndex: Int, tagName: String): Int {
        val len = html.length
        val allowNesting = tagName in NESTABLE_RAW_TAGS
        var depth = 1
        var cursor = startIndex

        while (cursor < len) {
            val lt = html.indexOf('<', cursor)
            if (lt == -1) return len

            if (allowNesting && html.startsWith("<!--", lt)) {
                val endComment = html.indexOf("-->", lt + 4)
                cursor = if (endComment == -1) len else endComment + 3
                continue
            }
            if (allowNesting && html.startsWith("<![CDATA[", lt)) {
                val endCdata = html.indexOf("]]>", lt + 9)
                cursor = if (endCdata == -1) len else endCdata + 3
                continue
            }

            // Closing tag: </tagName\s*>
            if (lt + 2 < len && html[lt + 1] == '/') {
                val nameStart = lt + 2
                val nameEnd = nameStart + tagName.length
                if (nameEnd <= len &&
                    html.regionMatches(nameStart, tagName, 0, tagName.length, ignoreCase = true) &&
                    (nameEnd == len || !isTagNameChar(html[nameEnd]))
                ) {
                    var scan = nameEnd
                    while (scan < len && html[scan].isWhitespace()) scan++
                    if (scan < len && html[scan] == '>') {
                        depth--
                        cursor = scan + 1
                        if (depth <= 0) {
                            return cursor
                        }
                        continue
                    }
                }
            } else if (allowNesting && lt + 1 < len && isAsciiLetter(html[lt + 1])) {
                val nameStart = lt + 1
                val nameEnd = nameStart + tagName.length
                if (nameEnd <= len &&
                    html.regionMatches(nameStart, tagName, 0, tagName.length, ignoreCase = true) &&
                    (nameEnd == len || !isTagNameChar(html[nameEnd]))
                ) {
                    val gt = findTagEnd(html, nameEnd)
                    if (gt != -1) {
                        var back = gt - 1
                        while (back > nameEnd && html[back].isWhitespace()) back--
                        val nestedSelfClosing = back >= nameEnd && html[back] == '/'
                        if (!nestedSelfClosing) {
                            depth++
                        }
                        cursor = gt + 1
                        continue
                    }
                }
            }

            cursor = lt + 1
        }
        return len
    }

    private fun findTagEnd(html: String, startIndex: Int): Int {
        val len = html.length
        var i = startIndex
        var quote: Char? = null
        while (i < len) {
            val ch = html[i]
            if (quote != null) {
                if (ch == quote) quote = null
            } else {
                if (ch == '"' || ch == '\'') {
                    quote = ch
                } else if (ch == '>') {
                    return i
                }
            }
            i++
        }
        return -1
    }

    private fun buildTree(tokens: List<HtmlToken>): HtmlNode.ElementNode {
        val root = HtmlNode.ElementNode("root", emptyMap())
        val stack = ArrayDeque<HtmlNode.ElementNode>()
        stack.addLast(root)

        fun popToAncestorIfPresent(targetNames: Set<String>, barrierNames: Set<String>) {
            for (idx in stack.lastIndex downTo 1) {
                val n = stack[idx].name
                if (n in barrierNames) break
                if (n in targetNames) {
                    while (stack.size > idx) {
                        stack.removeLast()
                    }
                    break
                }
            }
        }

        for (token in tokens) {
            when (token) {
                is HtmlToken.Text -> {
                    stack.last().children.add(HtmlNode.TextNode(token.raw, token.decodeEntities))
                }
                is HtmlToken.StartTag -> {
                    if (token.name in AUTO_CLOSE_P_TAGS) {
                        popToAncestorIfPresent(setOf("p"), setOf("div", "section", "article", "td", "th", "li", "blockquote"))
                    }
                    if (token.name == "li") {
                        popToAncestorIfPresent(setOf("li"), setOf("ul", "ol", "table"))
                    }
                    if (token.name == "dt" || token.name == "dd") {
                        popToAncestorIfPresent(setOf("dt", "dd"), setOf("dl", "table"))
                    }
                    if (token.name == "td" || token.name == "th") {
                        popToAncestorIfPresent(setOf("td", "th"), setOf("tr", "table"))
                    }
                    if (token.name == "tr") {
                        popToAncestorIfPresent(setOf("tr"), setOf("thead", "tbody", "tfoot", "table"))
                    }
                    val element = HtmlNode.ElementNode(token.name, token.attrs)
                    stack.last().children.add(element)
                    if (!token.selfClosing && token.name !in VOID_TAGS && stack.size <= MAX_DOM_DEPTH) {
                        stack.addLast(element)
                    }
                }
                is HtmlToken.EndTag -> {
                    var matchIndex = -1
                    for (idx in stack.lastIndex downTo 1) {
                        if (stack[idx].name == token.name) {
                            matchIndex = idx
                            break
                        }
                    }
                    if (matchIndex != -1) {
                        while (stack.size > matchIndex) {
                            stack.removeLast()
                        }
                    }
                }
            }
        }
        return root
    }

    private fun emitChildrenBlocks(
        children: List<HtmlNode>,
        blocks: MutableList<ReaderBlock>,
        nextId: (String) -> String,
        depth: Int = 0
    ) {
        if (depth > MAX_DOM_DEPTH) return
        val looseNodes = mutableListOf<HtmlNode>()

        fun flushLoose() {
            if (looseNodes.isEmpty()) return
            val hasOnlyRawText = looseNodes.all { it is HtmlNode.TextNode }
            if (hasOnlyRawText) {
                val decoded = looseNodes.joinToString("") { node ->
                    val textNode = node as HtmlNode.TextNode
                    if (textNode.decodeEntities) decodeEntities(textNode.raw) else textNode.raw
                }
                if (decoded.isNotBlank()) {
                    decoded.split(BLANK_LINE_REGEX).forEach { chunk ->
                        val collapsed = collapseWhitespace(chunk)
                        if (collapsed.isNotEmpty()) {
                            blocks.add(
                                ReaderBlock.Paragraph(
                                    id = nextId("p"),
                                    runs = listOf(TextRun(text = collapsed))
                                )
                            )
                        }
                    }
                }
            } else {
                val wrappedImages = collectDirectOrWrappedImages(looseNodes)
                val runs = collectRunsFromNodes(looseNodes)
                if (runs.isNotEmpty()) {
                    blocks.add(ReaderBlock.Paragraph(id = nextId("p"), runs = runs))
                }
                wrappedImages.forEach { img ->
                    extractImageBlock(img, caption = null, nextId = nextId)?.let { blocks.add(it) }
                }
            }
            looseNodes.clear()
        }

        for (child in children) {
            when (child) {
                is HtmlNode.TextNode -> looseNodes.add(child)
                is HtmlNode.ElementNode -> {
                    if (child.name in IGNORED_RAW_TAGS) continue
                    if (child.name == "img") {
                        flushLoose()
                        extractImageBlock(child, caption = null, nextId = nextId)?.let { blocks.add(it) }
                    } else if (child.name in BLOCK_TAGS) {
                        flushLoose()
                        emitBlockElement(child, blocks, nextId, depth + 1)
                    } else {
                        looseNodes.add(child)
                    }
                }
            }
        }
        flushLoose()
    }

    private fun emitBlockElement(
        element: HtmlNode.ElementNode,
        blocks: MutableList<ReaderBlock>,
        nextId: (String) -> String,
        depth: Int = 0
    ) {
        if (depth > MAX_DOM_DEPTH || element.name in IGNORED_RAW_TAGS) return

        when (element.name) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.name.substring(1).toIntOrNull()?.coerceIn(1, 4) ?: 2
                val runs = collectRunsFromNodes(element.children)
                val text = runs.joinToString("") { it.text }.trim()
                if (text.isNotEmpty()) {
                    blocks.add(ReaderBlock.Heading(id = nextId("h"), level = level, text = text))
                }
            }
            "p" -> {
                val images = collectDirectOrWrappedImages(element.children)
                val runs = collectRunsFromNodes(element.children)
                if (runs.isNotEmpty()) {
                    blocks.add(ReaderBlock.Paragraph(id = nextId("p"), runs = runs))
                }
                images.forEach { img ->
                    extractImageBlock(img, caption = null, nextId = nextId)?.let { blocks.add(it) }
                }
            }
            "blockquote" -> {
                val text = collectQuoteText(element)
                if (text.isNotEmpty()) {
                    blocks.add(ReaderBlock.Quote(id = nextId("q"), text = text))
                }
            }
            "pre" -> {
                val code = collectPreText(element)
                if (code.isNotBlank()) {
                    blocks.add(ReaderBlock.CodeBlock(id = nextId("code"), code = code))
                }
            }
            "ul", "ol" -> {
                emitList(element, blocks, nextId, indentLevel = 0, depth = depth + 1)
            }
            "dl" -> {
                emitDefinitionList(element, blocks, nextId)
            }
            "table" -> {
                emitTable(element, blocks, nextId, externalCaption = null, depth = depth + 1)
            }
            "figure" -> {
                emitFigure(element, blocks, nextId, depth = depth + 1)
            }
            "hr" -> {
                blocks.add(ReaderBlock.Divider(id = nextId("hr")))
            }
            else -> {
                emitChildrenBlocks(element.children, blocks, nextId, depth + 1)
            }
        }
    }

    private fun emitFigure(
        figure: HtmlNode.ElementNode,
        blocks: MutableList<ReaderBlock>,
        nextId: (String) -> String,
        depth: Int = 0
    ) {
        if (depth > MAX_DOM_DEPTH) return
        val captionNode = findFirstDescendant(figure, "figcaption")
        val captionText = captionNode?.let {
            collectRunsFromNodes(it.children).joinToString("") { r -> r.text }.trim()
        }?.takeIf { it.isNotEmpty() }

        val tableNode = findFirstDescendant(figure, "table")
        if (tableNode != null) {
            emitTable(tableNode, blocks, nextId, externalCaption = captionText, depth = depth + 1)
            return
        }

        val preNode = findFirstDescendant(figure, "pre")
        if (preNode != null) {
            val code = collectPreText(preNode)
            if (code.isNotBlank()) {
                blocks.add(ReaderBlock.CodeBlock(id = nextId("code"), code = code))
            }
            if (captionText != null) {
                blocks.add(
                    ReaderBlock.Paragraph(
                        id = nextId("p"),
                        runs = listOf(TextRun(text = captionText, italic = true))
                    )
                )
            }
            return
        }

        val imgNodes = findAllDescendants(figure, "img")
        if (imgNodes.isNotEmpty()) {
            val extracted = imgNodes.mapNotNull { extractImageBlock(it, caption = null, nextId = nextId) }
            if (extracted.isNotEmpty()) {
                extracted.forEachIndexed { index, block ->
                    val withCaption = if (index == extracted.lastIndex && captionText != null) {
                        block.copy(caption = captionText)
                    } else {
                        block
                    }
                    blocks.add(withCaption)
                }
                return
            }
        }

        emitChildrenBlocks(figure.children, blocks, nextId, depth + 1)
    }

    private data class RawTableCell(
        val runs: List<TextRun>,
        val colspan: Int,
        val rowspan: Int,
        val isTh: Boolean,
        val hasImage: Boolean
    )

    private fun emitTable(
        table: HtmlNode.ElementNode,
        blocks: MutableList<ReaderBlock>,
        nextId: (String) -> String,
        externalCaption: String?,
        depth: Int = 0
    ) {
        if (depth > MAX_DOM_DEPTH) return
        val captionText = findDirectOrSectionCaption(table) ?: externalCaption
        val theadRows = mutableListOf<HtmlNode.ElementNode>()
        val bodyRows = mutableListOf<HtmlNode.ElementNode>()
        val tfootRows = mutableListOf<HtmlNode.ElementNode>()

        for (child in table.children) {
            if (child !is HtmlNode.ElementNode) continue
            when (child.name) {
                "thead" -> collectRowsFromSection(child, theadRows)
                "tbody" -> collectRowsFromSection(child, bodyRows)
                "tfoot" -> collectRowsFromSection(child, tfootRows)
                "tr" -> bodyRows.add(child)
            }
        }

        val parsedThead = resolveSectionGrid(theadRows)
        val parsedBody = resolveSectionGrid(bodyRows)
        val parsedTfoot = resolveSectionGrid(tfootRows)

        val allBodyRows = parsedBody + parsedTfoot

        var headerCells: List<List<TextRun>> = emptyList()
        val dataRows = mutableListOf<List<List<TextRun>>>()

        when {
            parsedThead.isNotEmpty() -> {
                headerCells = parsedThead.first().cells
                // If thead had multiple rows, keep extra header rows as top data rows so no info is lost
                parsedThead.drop(1).forEach { dataRows.add(it.cells) }
                allBodyRows.forEach { dataRows.add(it.cells) }
            }
            allBodyRows.isNotEmpty() && allBodyRows.first().isHeaderRow && allBodyRows.size > 1 -> {
                headerCells = allBodyRows.first().cells
                allBodyRows.drop(1).forEach { dataRows.add(it.cells) }
            }
            else -> {
                allBodyRows.forEach { dataRows.add(it.cells) }
            }
        }

        val maxCols = maxOf(
            headerCells.size,
            dataRows.maxOfOrNull { it.size } ?: 0
        )

        // Single-cell layout tables with no header should unwrap as regular article blocks.
        if (maxCols <= 1 && headerCells.isEmpty() && dataRows.size <= 1) {
            emitChildrenBlocks(table.children, blocks, nextId, depth + 1)
            return
        }

        if (maxCols == 0 || (headerCells.isEmpty() && dataRows.isEmpty())) {
            return
        }

        val normalizedHeaders = if (headerCells.isNotEmpty()) {
            padRowToColumnCount(headerCells, maxCols)
        } else {
            emptyList()
        }
        val normalizedRows = dataRows.map { padRowToColumnCount(it, maxCols) }

        blocks.add(
            ReaderBlock.Table(
                id = nextId("table"),
                headers = normalizedHeaders,
                rows = normalizedRows,
                caption = captionText
            )
        )
    }

    private data class ParsedTableRow(
        val cells: List<List<TextRun>>,
        val isHeaderRow: Boolean
    )

    private fun resolveSectionGrid(rows: List<HtmlNode.ElementNode>): List<ParsedTableRow> {
        if (rows.isEmpty()) return emptyList()
        val pendingRowSpans = mutableListOf<Int>()
        val result = mutableListOf<ParsedTableRow>()

        for (tr in rows) {
            val rawCells = parseRawTableCells(tr)
            if (rawCells.isEmpty()) continue
            val rowCells = mutableListOf<List<TextRun>>()
            var colIdx = 0
            var thCount = 0
            var tdCount = 0
            var hasContent = false

            fun consumeOccupiedColumn(): Boolean {
                if (colIdx < pendingRowSpans.size && pendingRowSpans[colIdx] > 0) {
                    pendingRowSpans[colIdx] = pendingRowSpans[colIdx] - 1
                    rowCells.add(emptyList())
                    colIdx++
                    return true
                }
                return false
            }

            for (rawCell in rawCells) {
                while (consumeOccupiedColumn()) {
                    // Advance past columns spanned from prior rows
                }
                if (rawCell.isTh) thCount++ else tdCount++
                if (rawCell.hasImage || rawCell.runs.any { it.text.isNotBlank() }) {
                    hasContent = true
                }
                rowCells.add(rawCell.runs)
                while (pendingRowSpans.size <= colIdx + rawCell.colspan - 1) {
                    pendingRowSpans.add(0)
                }
                if (rawCell.rowspan > 1) {
                    for (offset in 0 until rawCell.colspan) {
                        val spanRemaining = rawCell.rowspan - 1
                        if (spanRemaining > pendingRowSpans[colIdx + offset]) {
                            pendingRowSpans[colIdx + offset] = spanRemaining
                        }
                    }
                }
                repeat(rawCell.colspan - 1) {
                    rowCells.add(emptyList())
                }
                colIdx += rawCell.colspan
            }
            while (colIdx < pendingRowSpans.size) {
                if (pendingRowSpans[colIdx] > 0) {
                    pendingRowSpans[colIdx] = pendingRowSpans[colIdx] - 1
                    rowCells.add(emptyList())
                }
                colIdx++
            }
            if (hasContent) {
                val isHeaderRow = thCount > 0 && tdCount == 0
                result.add(ParsedTableRow(cells = rowCells, isHeaderRow = isHeaderRow))
            }
        }
        return result
    }

    private fun parseRawTableCells(tr: HtmlNode.ElementNode): List<RawTableCell> {
        val rawCells = mutableListOf<RawTableCell>()
        for (child in tr.children) {
            if (child !is HtmlNode.ElementNode) continue
            if (child.name != "th" && child.name != "td") continue
            val isTh = child.name == "th"
            var runs = collectRunsFromNodes(child.children)
            var hasImage = false
            if (runs.isEmpty()) {
                val cellImages = findAllDescendants(child, "img")
                    .mapNotNull { extractImageBlock(it, caption = null) { _ -> "cell_img" } }
                if (cellImages.isNotEmpty()) {
                    hasImage = true
                    runs = cellImages.map { img ->
                        TextRun(
                            text = img.alt ?: "[Image]",
                            linkUrl = img.url
                        )
                    }
                }
            }
            val colspan = child.attrs["colspan"]?.trim()?.toIntOrNull()?.coerceIn(1, 12) ?: 1
            val rowspan = child.attrs["rowspan"]?.trim()?.toIntOrNull()?.coerceIn(1, 32) ?: 1
            rawCells.add(RawTableCell(runs = runs, colspan = colspan, rowspan = rowspan, isTh = isTh, hasImage = hasImage))
        }
        return rawCells
    }

    private fun padRowToColumnCount(
        row: List<List<TextRun>>,
        columnCount: Int
    ): List<List<TextRun>> {
        if (row.size == columnCount) return row
        if (row.size > columnCount) return row.take(columnCount)
        return row + List(columnCount - row.size) { emptyList() }
    }

    private fun collectRowsFromSection(
        section: HtmlNode.ElementNode,
        out: MutableList<HtmlNode.ElementNode>
    ) {
        for (child in section.children) {
            if (child is HtmlNode.ElementNode && child.name == "tr") {
                out.add(child)
            }
        }
    }

    private fun findDirectOrSectionCaption(table: HtmlNode.ElementNode): String? {
        val captionNode = table.children
            .filterIsInstance<HtmlNode.ElementNode>()
            .firstOrNull { it.name == "caption" }
            ?: return null
        return collectRunsFromNodes(captionNode.children)
            .joinToString("") { it.text }
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun emitDefinitionList(
        dl: HtmlNode.ElementNode,
        blocks: MutableList<ReaderBlock>,
        nextId: (String) -> String
    ) {
        val flatItems = mutableListOf<HtmlNode.ElementNode>()
        fun collectDtDd(nodes: List<HtmlNode>, depth: Int = 0) {
            if (depth > MAX_DOM_DEPTH) return
            for (node in nodes) {
                if (node !is HtmlNode.ElementNode || node.name in IGNORED_RAW_TAGS) continue
                when (node.name) {
                    "dt", "dd" -> flatItems.add(node)
                    "div" -> collectDtDd(node.children, depth + 1)
                }
            }
        }
        collectDtDd(dl.children)

        var currentTerm: List<TextRun>? = null
        val currentDefs = mutableListOf<List<TextRun>>()

        fun flushPair() {
            val term = currentTerm
            if (term != null && (term.isNotEmpty() || currentDefs.isNotEmpty())) {
                blocks.add(
                    ReaderBlock.DefinitionItem(
                        id = nextId("def"),
                        term = term,
                        definitions = currentDefs.toList()
                    )
                )
            } else if (term == null && currentDefs.isNotEmpty()) {
                currentDefs.forEach { defRuns ->
                    blocks.add(ReaderBlock.Paragraph(id = nextId("p"), runs = defRuns))
                }
            }
            currentTerm = null
            currentDefs.clear()
        }

        for (item in flatItems) {
            val runs = collectRunsFromNodes(item.children)
            if (runs.isEmpty()) continue
            if (item.name == "dt") {
                if (currentTerm != null && currentDefs.isNotEmpty()) {
                    flushPair()
                }
                currentTerm = if (currentTerm == null) runs else currentTerm!! + TextRun(" / ") + runs
            } else {
                currentDefs.add(runs)
            }
        }
        flushPair()
    }

    private val SRCSET_DESCRIPTOR_REGEX = Regex("""^\d+(?:\.\d+)?[wx]$""", RegexOption.IGNORE_CASE)

    /**
     * Parses an HTML `srcset` attribute without splitting commas inside CDN URL path segments
     * (e.g. Cloudinary / Substack / Medium `w_800,c_limit,f_auto/... 800w`) and skips `data:` placeholders.
     */
    internal fun parseFirstSrcsetUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        var i = 0
        val len = s.length
        while (i < len) {
            while (i < len && (s[i].isWhitespace() || s[i] == ',')) i++
            if (i >= len) break
            val urlStart = i
            while (i < len && !s[i].isWhitespace()) {
                // Commas inside URLs are valid unless followed by whitespace or end-of-string
                if (s[i] == ',' && (i + 1 >= len || s[i + 1].isWhitespace())) {
                    break
                }
                i++
            }
            var candidate = s.substring(urlStart, i).trimEnd(',')
            // Skip optional descriptor(s) up to next candidate separator comma
            var inParens = false
            while (i < len) {
                val c = s[i]
                if (c == '(') inParens = true
                else if (c == ')') inParens = false
                else if (c == ',' && !inParens) {
                    i++
                    break
                }
                i++
            }
            if (candidate.isNotBlank() &&
                !SRCSET_DESCRIPTOR_REGEX.matches(candidate) &&
                !candidate.startsWith("data:", ignoreCase = true)
            ) {
                return candidate
            }
        }
        return null
    }

    private fun extractImageBlock(
        img: HtmlNode.ElementNode,
        caption: String?,
        nextId: (String) -> String
    ): ReaderBlock.Image? {
        val candidates = listOf(
            img.attrs["src"],
            img.attrs["data-src"],
            img.attrs["data-original"],
            img.attrs["data-lazy-src"],
            parseFirstSrcsetUrl(img.attrs["srcset"]),
            parseFirstSrcsetUrl(img.attrs["data-srcset"])
        )
        val url = candidates.firstNotNullOfOrNull { sanitizeHttpUrl(it) } ?: return null

        // Skip 1x1 or 2x2 tracking pixels
        val widthPx = img.attrs["width"]?.trim()?.removeSuffix("px")?.toIntOrNull()
        val heightPx = img.attrs["height"]?.trim()?.removeSuffix("px")?.toIntOrNull()
        if ((widthPx != null && widthPx <= 2) || (heightPx != null && heightPx <= 2)) {
            return null
        }
        val lowerUrl = url.lowercase(java.util.Locale.ROOT)
        if (lowerUrl.contains("1x1.") || lowerUrl.endsWith("/pixel.gif") || lowerUrl.endsWith("/spacer.gif")) {
            return null
        }

        val alt = img.attrs["alt"]?.trim()?.takeIf { it.isNotEmpty() }
        return ReaderBlock.Image(
            id = nextId("img"),
            url = url,
            alt = alt,
            caption = caption
        )
    }

    private fun collectDirectOrWrappedImages(nodes: List<HtmlNode>): List<HtmlNode.ElementNode> {
        val out = mutableListOf<HtmlNode.ElementNode>()
        fun walk(list: List<HtmlNode>, depth: Int = 0) {
            if (depth > MAX_DOM_DEPTH) return
            for (n in list) {
                if (n !is HtmlNode.ElementNode || n.name in IGNORED_RAW_TAGS) continue
                if (n.name == "img") {
                    out.add(n)
                } else if (n.name !in BLOCK_TAGS) {
                    walk(n.children, depth + 1)
                }
            }
        }
        walk(nodes)
        return out
    }

    private fun findFirstDescendant(
        parent: HtmlNode.ElementNode,
        targetName: String,
        depth: Int = 0
    ): HtmlNode.ElementNode? {
        if (depth > MAX_DOM_DEPTH) return null
        for (child in parent.children) {
            if (child !is HtmlNode.ElementNode || child.name in IGNORED_RAW_TAGS) continue
            if (child.name == targetName) return child
            val nested = findFirstDescendant(child, targetName, depth + 1)
            if (nested != null) return nested
        }
        return null
    }

    private fun findAllDescendants(
        parent: HtmlNode.ElementNode,
        targetName: String,
        depth: Int = 0,
        out: MutableList<HtmlNode.ElementNode> = mutableListOf()
    ): List<HtmlNode.ElementNode> {
        if (depth > MAX_DOM_DEPTH) return out
        for (child in parent.children) {
            if (child !is HtmlNode.ElementNode || child.name in IGNORED_RAW_TAGS) continue
            if (child.name == targetName) {
                out.add(child)
            } else {
                findAllDescendants(child, targetName, depth + 1, out)
            }
        }
        return out
    }

    private fun emitList(
        listNode: HtmlNode.ElementNode,
        blocks: MutableList<ReaderBlock>,
        nextId: (String) -> String,
        indentLevel: Int,
        depth: Int = 0
    ) {
        if (depth > MAX_DOM_DEPTH) return
        val isOrdered = listNode.name == "ol"
        var index = if (isOrdered) (listNode.attrs["start"]?.toIntOrNull() ?: 1) else 1
        val clampedIndent = indentLevel.coerceIn(0, 4)

        for (child in listNode.children) {
            if (child !is HtmlNode.ElementNode) continue
            when (child.name) {
                "li" -> {
                    val runs = collectListItemRuns(child)
                    if (runs.isNotEmpty()) {
                        blocks.add(
                            ReaderBlock.BulletItem(
                                id = nextId("li"),
                                runs = runs,
                                orderedIndex = if (isOrdered) index else null,
                                indentLevel = clampedIndent
                            )
                        )
                        index++
                    }
                    emitNestedLists(child.children, blocks, nextId, clampedIndent + 1, depth + 1)
                }
                "ul", "ol" -> emitList(child, blocks, nextId, clampedIndent + 1, depth + 1)
                else -> emitBlockElement(child, blocks, nextId, depth + 1)
            }
        }
    }

    private fun emitNestedLists(
        nodes: List<HtmlNode>,
        blocks: MutableList<ReaderBlock>,
        nextId: (String) -> String,
        nextIndentLevel: Int,
        depth: Int = 0
    ) {
        if (depth > MAX_DOM_DEPTH) return
        for (node in nodes) {
            if (node !is HtmlNode.ElementNode || node.name in IGNORED_RAW_TAGS) continue
            if (node.name == "ul" || node.name == "ol") {
                emitList(node, blocks, nextId, nextIndentLevel, depth + 1)
            } else {
                emitNestedLists(node.children, blocks, nextId, nextIndentLevel, depth + 1)
            }
        }
    }

    private fun collectListItemRuns(liNode: HtmlNode.ElementNode): List<TextRun> {
        val acc = RunAccumulator()
        for (node in liNode.children) {
            appendInlineNode(node, InlineStyle(), acc, skipNestedLists = true, depth = 0)
        }
        return acc.build()
    }

    private fun collectQuoteText(quoteNode: HtmlNode.ElementNode, depth: Int = 0): String {
        if (depth > MAX_DOM_DEPTH) return ""
        val segments = mutableListOf<String>()
        val inlineBuffer = mutableListOf<HtmlNode>()

        fun flushInline() {
            if (inlineBuffer.isEmpty()) return
            val text = collectRunsFromNodes(inlineBuffer).joinToString("") { it.text }.trim()
            if (text.isNotEmpty()) segments.add(text)
            inlineBuffer.clear()
        }

        for (child in quoteNode.children) {
            if (child is HtmlNode.ElementNode && child.name in IGNORED_RAW_TAGS) continue
            if (child is HtmlNode.ElementNode && child.name in BLOCK_TAGS) {
                flushInline()
                val inner = if (child.name == "blockquote") {
                    collectQuoteText(child, depth + 1)
                } else {
                    collectRunsFromNodes(child.children).joinToString("") { it.text }.trim()
                }
                if (inner.isNotEmpty()) segments.add(inner)
            } else {
                inlineBuffer.add(child)
            }
        }
        flushInline()
        return segments.joinToString("\n\n").trim()
    }

    private fun collectPreText(preNode: HtmlNode.ElementNode): String {
        val sb = StringBuilder()

        fun appendPre(node: HtmlNode, depth: Int = 0) {
            if (depth > MAX_DOM_DEPTH) return
            when (node) {
                is HtmlNode.TextNode -> sb.append(if (node.decodeEntities) decodeEntities(node.raw) else node.raw)
                is HtmlNode.ElementNode -> {
                    if (node.name in IGNORED_RAW_TAGS) return
                    if (node.name == "br") {
                        sb.append('\n')
                        return
                    }
                    val beforeLen = sb.length
                    for (child in node.children) {
                        appendPre(child, depth + 1)
                    }
                    if ((node.name == "p" || node.name == "div") && sb.length > beforeLen) {
                        if (!sb.endsWith("\n")) sb.append('\n')
                    }
                }
            }
        }

        for (child in preNode.children) {
            appendPre(child, depth = 0)
        }
        return sb.toString()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim('\n')
    }

    private fun collectRunsFromNodes(nodes: List<HtmlNode>): List<TextRun> {
        val acc = RunAccumulator()
        for (node in nodes) {
            appendInlineNode(node, InlineStyle(), acc, skipNestedLists = false, depth = 0)
        }
        return acc.build()
    }

    private fun appendInlineNode(
        node: HtmlNode,
        style: InlineStyle,
        acc: RunAccumulator,
        skipNestedLists: Boolean,
        depth: Int = 0
    ) {
        if (depth > MAX_DOM_DEPTH) return
        when (node) {
            is HtmlNode.TextNode -> {
                val resolved = if (node.decodeEntities) decodeEntities(node.raw) else node.raw
                acc.appendText(resolved, style)
            }
            is HtmlNode.ElementNode -> {
                if (node.name in IGNORED_RAW_TAGS) return
                if (skipNestedLists && (node.name == "ul" || node.name == "ol")) return
                when (node.name) {
                    "br" -> acc.appendLineBreak(style)
                    "strong", "b" -> {
                        val next = style.copy(bold = true)
                        node.children.forEach { appendInlineNode(it, next, acc, skipNestedLists, depth + 1) }
                    }
                    "em", "i" -> {
                        val next = style.copy(italic = true)
                        node.children.forEach { appendInlineNode(it, next, acc, skipNestedLists, depth + 1) }
                    }
                    "code", "kbd", "samp" -> {
                        val next = style.copy(code = true)
                        node.children.forEach { appendInlineNode(it, next, acc, skipNestedLists, depth + 1) }
                    }
                    "del", "s", "strike" -> {
                        val next = style.copy(strikethrough = true)
                        node.children.forEach { appendInlineNode(it, next, acc, skipNestedLists, depth + 1) }
                    }
                    "a" -> {
                        val href = sanitizeHttpUrl(node.attrs["href"])
                        val next = if (href != null) style.copy(linkUrl = href) else style
                        node.children.forEach { appendInlineNode(it, next, acc, skipNestedLists, depth + 1) }
                    }
                    "p", "div", "li" -> {
                        if (acc.hasEmittedAny()) acc.appendLineBreak(style)
                        node.children.forEach { appendInlineNode(it, style, acc, skipNestedLists, depth + 1) }
                        if (acc.hasEmittedAny()) acc.appendLineBreak(style)
                    }
                    else -> {
                        node.children.forEach { appendInlineNode(it, style, acc, skipNestedLists, depth + 1) }
                    }
                }
            }
        }
    }

    private fun extractPlainFallbackText(root: HtmlNode.ElementNode): String {
        val sb = StringBuilder()

        fun walk(node: HtmlNode, depth: Int = 0) {
            if (depth > MAX_DOM_DEPTH) return
            when (node) {
                is HtmlNode.TextNode -> sb.append(if (node.decodeEntities) decodeEntities(node.raw) else node.raw)
                is HtmlNode.ElementNode -> {
                    if (node.name in IGNORED_RAW_TAGS) return
                    if (node.name == "br") {
                        sb.append('\n')
                        return
                    }
                    val isBlock = node.name in BLOCK_TAGS && node.name != "root"
                    if (isBlock && sb.isNotEmpty() && !sb.endsWith("\n\n")) {
                        sb.append("\n\n")
                    }
                    for (child in node.children) {
                        walk(child, depth + 1)
                    }
                    if (isBlock && sb.isNotEmpty() && !sb.endsWith("\n\n")) {
                        sb.append("\n\n")
                    }
                }
            }
        }

        walk(root)
        return sb.toString().trim()
    }

    private fun collapseWhitespace(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length)
        var pendingSpace = false
        for (ch in text) {
            if (ch.isHtmlWhitespace()) {
                if (sb.isNotEmpty()) pendingSpace = true
            } else {
                if (pendingSpace) {
                    sb.append(' ')
                    pendingSpace = false
                }
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private class RunAccumulator {
        private val runs = mutableListOf<TextRun>()
        private val currentBuffer = StringBuilder()
        private var currentStyle: InlineStyle? = null
        private var pendingSpace = false
        private var pendingSpaceStyle = InlineStyle()
        private var hasEmittedContent = false
        private var atLineStart = true

        fun hasEmittedAny(): Boolean = hasEmittedContent

        fun appendText(text: String, style: InlineStyle) {
            for (ch in text) {
                if (ch.isHtmlWhitespace()) {
                    if (!atLineStart) {
                        if (!pendingSpace) {
                            pendingSpace = true
                            pendingSpaceStyle = style
                        } else if (style.linkUrl == null && pendingSpaceStyle.linkUrl != null) {
                            pendingSpaceStyle = style
                        }
                    }
                } else {
                    if (pendingSpace) {
                        val spaceStyle = if (pendingSpaceStyle.linkUrl != null &&
                            pendingSpaceStyle.linkUrl != style.linkUrl
                        ) {
                            pendingSpaceStyle.copy(linkUrl = null)
                        } else {
                            pendingSpaceStyle
                        }
                        appendChar(' ', spaceStyle)
                        pendingSpace = false
                    }
                    appendChar(ch, style)
                    hasEmittedContent = true
                    atLineStart = false
                }
            }
        }

        fun appendLineBreak(style: InlineStyle) {
            pendingSpace = false
            if (hasEmittedContent && !atLineStart) {
                appendChar('\n', style.copy(linkUrl = null))
                atLineStart = true
            }
        }

        fun build(): List<TextRun> {
            flushBuffer()
            if (runs.isEmpty()) return emptyList()

            // Trim any trailing newlines left by trailing <br>
            while (runs.isNotEmpty()) {
                val last = runs.last()
                val trimmed = last.text.trimEnd('\n')
                if (trimmed.isEmpty()) {
                    runs.removeAt(runs.lastIndex)
                } else if (trimmed.length != last.text.length) {
                    runs[runs.lastIndex] = last.copy(text = trimmed)
                    break
                } else {
                    break
                }
            }

            if (runs.isEmpty()) return emptyList()

            // Merge adjacent runs with identical formatting without O(N^2) string copies
            val merged = ArrayList<TextRun>(runs.size)
            var idx = 0
            while (idx < runs.size) {
                val base = runs[idx]
                if (base.text.isEmpty()) {
                    idx++
                    continue
                }
                var end = idx + 1
                var totalLen = base.text.length
                while (end < runs.size) {
                    val next = runs[end]
                    if (next.text.isEmpty()) {
                        end++
                        continue
                    }
                    if (base.bold == next.bold &&
                        base.italic == next.italic &&
                        base.code == next.code &&
                        base.strikethrough == next.strikethrough &&
                        base.linkUrl == next.linkUrl
                    ) {
                        totalLen += next.text.length
                        end++
                    } else {
                        break
                    }
                }
                if (totalLen == base.text.length) {
                    merged.add(base)
                } else {
                    val sb = StringBuilder(totalLen)
                    for (k in idx until end) {
                        val t = runs[k].text
                        if (t.isNotEmpty()) sb.append(t)
                    }
                    merged.add(base.copy(text = sb.toString()))
                }
                idx = end
            }
            return merged
        }

        private fun appendChar(ch: Char, style: InlineStyle) {
            if (currentStyle != style) {
                flushBuffer()
                currentStyle = style
            }
            currentBuffer.append(ch)
        }

        private fun flushBuffer() {
            val style = currentStyle ?: return
            if (currentBuffer.isNotEmpty()) {
                runs.add(
                    TextRun(
                        text = currentBuffer.toString(),
                        bold = style.bold,
                        italic = style.italic,
                        code = style.code,
                        linkUrl = style.linkUrl,
                        strikethrough = style.strikethrough
                    )
                )
                currentBuffer.setLength(0)
            }
        }
    }

    private fun Char.isHtmlWhitespace(): Boolean =
        this == ' ' || this == '\t' || this == '\n' || this == '\r' || this == '\u000C'

    private fun isAsciiLetter(ch: Char): Boolean =
        ch in 'a'..'z' || ch in 'A'..'Z'

    private fun isTagNameChar(ch: Char): Boolean =
        isAsciiLetter(ch) || ch in '0'..'9' || ch == '-' || ch == ':'
}
