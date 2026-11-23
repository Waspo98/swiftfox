package app.overbay.swiftfox.features.reader

/**
 * Pure-Kotlin HTML-to-[ReaderBlock] parser for Readability output and plain-text fallbacks.
 * Avoids `android.text.Html` so JVM unit tests run without Robolectric.
 */
object ReaderHtmlParser {

    private sealed interface HtmlToken {
        data class Text(val raw: String) : HtmlToken
        data class StartTag(
            val name: String,
            val attrs: Map<String, String>,
            val selfClosing: Boolean
        ) : HtmlToken
        data class EndTag(val name: String) : HtmlToken
    }

    private sealed interface HtmlNode {
        data class TextNode(val raw: String) : HtmlNode
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
        val linkUrl: String? = null
    )

    private val VOID_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    private val IGNORED_RAW_TAGS = setOf("script", "style", "noscript", "template", "svg", "head")

    private val BLOCK_TAGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6",
        "p", "blockquote", "pre", "ul", "ol", "li", "hr",
        "div", "section", "article", "main", "header", "footer", "aside",
        "figure", "figcaption", "table", "thead", "tbody", "tfoot", "tr", "td", "th",
        "dl", "dt", "dd"
    )

    private val AUTO_CLOSE_P_TAGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6",
        "p", "blockquote", "pre", "ul", "ol", "hr",
        "div", "section", "article", "main", "table"
    )

    private val ENTITY_REGEX = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z][a-zA-Z0-9]*);")
    private val BLANK_LINE_REGEX = Regex("\\r?\\n\\s*\\r?\\n")

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "mdash" to "\u2014",
        "ndash" to "\u2013",
        "hellip" to "\u2026",
        "lsquo" to "\u2018",
        "rsquo" to "\u2019",
        "ldquo" to "\u201C",
        "rdquo" to "\u201D",
        "laquo" to "\u00AB",
        "raquo" to "\u00BB",
        "bull" to "\u2022",
        "middot" to "\u00B7",
        "copy" to "\u00A9",
        "reg" to "\u00AE",
        "trade" to "\u2122",
        "times" to "\u00D7",
        "divide" to "\u00F7",
        "plusmn" to "\u00B1"
    )

    fun parse(html: String): List<ReaderBlock> {
        if (html.isBlank()) return emptyList()

        val tokens = tokenize(html)
        val root = buildTree(tokens)
        val blocks = mutableListOf<ReaderBlock>()
        var nextId = 0

        fun generateId(prefix: String): String = "${prefix}_${nextId++}"

        emitChildrenBlocks(root.children, blocks, ::generateId)

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
                else -> NAMED_ENTITIES[entity] ?: NAMED_ENTITIES[entity.lowercase()] ?: match.value
            }
        }
    }

    private fun codePointToString(codePoint: Int?): String? {
        if (codePoint == null || codePoint <= 0 || codePoint > 0x10FFFF) return null
        if (codePoint in 0xD800..0xDFFF) return null
        return String(Character.toChars(codePoint))
    }

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
                val tagName = html.substring(nameStart, cursor).lowercase()
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
            val tagName = html.substring(lt + 1, cursor).lowercase()

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
                val attrName = html.substring(attrStart, cursor).lowercase()
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

            // Skip raw contents of script/style/noscript/template tags so internal `<` cannot corrupt parsing.
            if (!selfClosing && tagName in IGNORED_RAW_TAGS && tagName != "head") {
                val closeTag = "</$tagName>"
                val closeIdx = html.indexOf(closeTag, i, ignoreCase = true)
                if (closeIdx != -1) {
                    tokens.add(HtmlToken.EndTag(tagName))
                    i = closeIdx + closeTag.length
                } else {
                    i = len
                }
            }
        }

        return tokens
    }

    private fun buildTree(tokens: List<HtmlToken>): HtmlNode.ElementNode {
        val root = HtmlNode.ElementNode("root", emptyMap())
        val stack = ArrayDeque<HtmlNode.ElementNode>()
        stack.addLast(root)

        for (token in tokens) {
            when (token) {
                is HtmlToken.Text -> {
                    stack.last().children.add(HtmlNode.TextNode(token.raw))
                }
                is HtmlToken.StartTag -> {
                    if (token.name in AUTO_CLOSE_P_TAGS && stack.last().name == "p") {
                        stack.removeLast()
                    }
                    if (token.name == "li" && stack.last().name == "li") {
                        stack.removeLast()
                    }
                    val element = HtmlNode.ElementNode(token.name, token.attrs)
                    stack.last().children.add(element)
                    if (!token.selfClosing && token.name !in VOID_TAGS) {
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
        nextId: (String) -> String
    ) {
        val looseNodes = mutableListOf<HtmlNode>()

        fun flushLoose() {
            if (looseNodes.isEmpty()) return
            val hasOnlyRawText = looseNodes.all { it is HtmlNode.TextNode }
            if (hasOnlyRawText) {
                val combined = looseNodes.joinToString("") { (it as HtmlNode.TextNode).raw }
                val decoded = decodeEntities(combined)
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
                val runs = collectRunsFromNodes(looseNodes)
                if (runs.isNotEmpty()) {
                    blocks.add(ReaderBlock.Paragraph(id = nextId("p"), runs = runs))
                }
            }
            looseNodes.clear()
        }

        for (child in children) {
            when (child) {
                is HtmlNode.TextNode -> looseNodes.add(child)
                is HtmlNode.ElementNode -> {
                    if (child.name in IGNORED_RAW_TAGS) continue
                    if (child.name in BLOCK_TAGS) {
                        flushLoose()
                        emitBlockElement(child, blocks, nextId)
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
        nextId: (String) -> String
    ) {
        if (element.name in IGNORED_RAW_TAGS) return

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
                val runs = collectRunsFromNodes(element.children)
                if (runs.isNotEmpty()) {
                    blocks.add(ReaderBlock.Paragraph(id = nextId("p"), runs = runs))
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
                emitList(element, blocks, nextId)
            }
            "hr" -> {
                blocks.add(ReaderBlock.Divider(id = nextId("hr")))
            }
            else -> {
                emitChildrenBlocks(element.children, blocks, nextId)
            }
        }
    }

    private fun emitList(
        listNode: HtmlNode.ElementNode,
        blocks: MutableList<ReaderBlock>,
        nextId: (String) -> String
    ) {
        val isOrdered = listNode.name == "ol"
        var index = if (isOrdered) (listNode.attrs["start"]?.toIntOrNull() ?: 1) else 1

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
                                orderedIndex = if (isOrdered) index else null
                            )
                        )
                        index++
                    }
                    emitNestedLists(child.children, blocks, nextId)
                }
                "ul", "ol" -> emitList(child, blocks, nextId)
                else -> emitBlockElement(child, blocks, nextId)
            }
        }
    }

    private fun emitNestedLists(
        nodes: List<HtmlNode>,
        blocks: MutableList<ReaderBlock>,
        nextId: (String) -> String
    ) {
        for (node in nodes) {
            if (node !is HtmlNode.ElementNode || node.name in IGNORED_RAW_TAGS) continue
            if (node.name == "ul" || node.name == "ol") {
                emitList(node, blocks, nextId)
            } else {
                emitNestedLists(node.children, blocks, nextId)
            }
        }
    }

    private fun collectListItemRuns(liNode: HtmlNode.ElementNode): List<TextRun> {
        val nonListChildren = liNode.children.filter { child ->
            child !is HtmlNode.ElementNode || (child.name != "ul" && child.name != "ol")
        }
        return collectRunsFromNodes(nonListChildren)
    }

    private fun collectQuoteText(quoteNode: HtmlNode.ElementNode): String {
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
                    collectQuoteText(child)
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

        fun appendPre(node: HtmlNode) {
            when (node) {
                is HtmlNode.TextNode -> sb.append(decodeEntities(node.raw))
                is HtmlNode.ElementNode -> {
                    if (node.name in IGNORED_RAW_TAGS) return
                    if (node.name == "br") {
                        sb.append('\n')
                        return
                    }
                    val beforeLen = sb.length
                    for (child in node.children) {
                        appendPre(child)
                    }
                    if ((node.name == "p" || node.name == "div") && sb.length > beforeLen) {
                        if (!sb.endsWith("\n")) sb.append('\n')
                    }
                }
            }
        }

        for (child in preNode.children) {
            appendPre(child)
        }
        return sb.toString()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim('\n')
    }

    private fun collectRunsFromNodes(nodes: List<HtmlNode>): List<TextRun> {
        val acc = RunAccumulator()
        for (node in nodes) {
            appendInlineNode(node, InlineStyle(), acc)
        }
        return acc.build()
    }

    private fun appendInlineNode(
        node: HtmlNode,
        style: InlineStyle,
        acc: RunAccumulator
    ) {
        when (node) {
            is HtmlNode.TextNode -> {
                acc.appendText(decodeEntities(node.raw), style)
            }
            is HtmlNode.ElementNode -> {
                if (node.name in IGNORED_RAW_TAGS) return
                when (node.name) {
                    "br" -> acc.appendLineBreak(style)
                    "strong", "b" -> {
                        val next = style.copy(bold = true)
                        node.children.forEach { appendInlineNode(it, next, acc) }
                    }
                    "em", "i" -> {
                        val next = style.copy(italic = true)
                        node.children.forEach { appendInlineNode(it, next, acc) }
                    }
                    "code", "kbd", "samp" -> {
                        val next = style.copy(code = true)
                        node.children.forEach { appendInlineNode(it, next, acc) }
                    }
                    "a" -> {
                        val href = node.attrs["href"]
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() && !it.startsWith("javascript:", ignoreCase = true) }
                        val next = if (href != null) style.copy(linkUrl = href) else style
                        node.children.forEach { appendInlineNode(it, next, acc) }
                    }
                    "p", "div", "li" -> {
                        if (acc.hasEmittedAny()) acc.appendLineBreak(style)
                        node.children.forEach { appendInlineNode(it, style, acc) }
                        if (acc.hasEmittedAny()) acc.appendLineBreak(style)
                    }
                    else -> {
                        node.children.forEach { appendInlineNode(it, style, acc) }
                    }
                }
            }
        }
    }

    private fun extractPlainFallbackText(root: HtmlNode.ElementNode): String {
        val sb = StringBuilder()

        fun walk(node: HtmlNode) {
            when (node) {
                is HtmlNode.TextNode -> sb.append(decodeEntities(node.raw))
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
                        walk(child)
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

            // Merge adjacent runs with identical formatting
            val merged = mutableListOf<TextRun>()
            for (run in runs) {
                if (run.text.isEmpty()) continue
                val prev = merged.lastOrNull()
                if (prev != null &&
                    prev.bold == run.bold &&
                    prev.italic == run.italic &&
                    prev.code == run.code &&
                    prev.linkUrl == run.linkUrl
                ) {
                    merged[merged.lastIndex] = prev.copy(text = prev.text + run.text)
                } else {
                    merged.add(run)
                }
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
                        linkUrl = style.linkUrl
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
