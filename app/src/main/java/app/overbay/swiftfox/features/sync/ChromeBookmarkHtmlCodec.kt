package app.overbay.swiftfox.features.sync

import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import java.util.Locale

data class ImportedBookmark(
    val title: String,
    val url: String,
    val folder: String,
    val addedAtMs: Long
)

/**
 * Standard Netscape Bookmark HTML (`NETSCAPE-Bookmark-file-1`) importer and exporter
 * compatible with Google Chrome, Firefox, Zen, Brave, Edge, and Safari exports.
 */
object ChromeBookmarkHtmlCodec {

    private val hrefAttrRegex = Regex("""\bHREF\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
    private val addDateAttrRegex = Regex("""\bADD_DATE\s*=\s*(?:"(\d+)"|'(\d+)'|(\d+))""", RegexOption.IGNORE_CASE)
    private val entityRegex = Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""")

    /**
     * Linear single-pass Netscape HTML bookmark parser. Avoids `DOT_MATCHES_ALL` regex
     * catastrophic backtracking (`O(N^2)`) on multi-MB exports or unclosed `<A>`/`<H3>` tags.
     */
    fun parse(html: String): List<ImportedBookmark> {
        if (html.isBlank()) return emptyList()

        val results = mutableListOf<ImportedBookmark>()
        val folderStack = ArrayDeque<String>()
        var pendingFolderName: String? = null
        var seenRootDl = false

        val len = html.length
        var idx = 0
        while (idx < len) {
            val lt = html.indexOf('<', idx)
            if (lt < 0 || lt + 2 >= len) break
            // Skip HTML comments <!-- ... -->
            if (html.startsWith("<!--", lt)) {
                val endComment = html.indexOf("-->", lt + 4)
                idx = if (endComment >= 0) endComment + 3 else len
                continue
            }
            val gt = findTagEnd(html, lt + 1)
            if (gt < 0) break

            val tagHeader = html.substring(lt + 1, gt).trim()
            when {
                matchesTagName(tagHeader, "DL") -> {
                    if (!seenRootDl) {
                        seenRootDl = true
                        pendingFolderName = null
                    } else {
                        folderStack.addLast(pendingFolderName ?: "Bookmarks")
                        pendingFolderName = null
                    }
                    idx = gt + 1
                }

                matchesClosingTagName(tagHeader, "DL") -> {
                    pendingFolderName = null
                    if (folderStack.isNotEmpty()) {
                        folderStack.removeLast()
                    }
                    idx = gt + 1
                }

                matchesTagName(tagHeader, "H3") -> {
                    val contentStart = gt + 1
                    val closeIdx = findClosingTagOrNextBlock(html, contentStart, "H3")
                    val rawName = html.substring(contentStart, closeIdx.first)
                    val cleaned = unescapeHtml(stripTags(rawName)).trim()
                    pendingFolderName = cleaned.takeIf { it.isNotEmpty() }
                    idx = closeIdx.second
                }

                matchesTagName(tagHeader, "A") -> {
                    // Stray <H3> without an immediately following <DL> must not attach to a later <DL>
                    pendingFolderName = null
                    val attrs = tagHeader.substring(1).trim()
                    val contentStart = gt + 1
                    val closeIdx = findClosingTagOrNextBlock(html, contentStart, "A")
                    val rawTitle = html.substring(contentStart, closeIdx.first)
                    idx = closeIdx.second

                    val hrefMatch = hrefAttrRegex.find(attrs) ?: continue
                    val rawHref = hrefMatch.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty()
                    val decodedHref = unescapeHtml(rawHref).trim()
                    if (!isSupportedUrl(decodedHref)) continue
                    val url = OmniboxEngine.canonicalizeUrlSchemeAndHost(decodedHref)

                    val addDateRaw = addDateAttrRegex.find(attrs)
                        ?.groupValues
                        ?.drop(1)
                        ?.firstOrNull { it.isNotEmpty() }
                        ?.toLongOrNull()
                    val addedAtMs = when {
                        addDateRaw == null || addDateRaw <= 0L -> System.currentTimeMillis()
                        addDateRaw > 1_000_000_000_000_000L -> addDateRaw / 1000L // microseconds
                        addDateRaw > 1_000_000_000_000L -> addDateRaw // already milliseconds
                        addDateRaw <= Long.MAX_VALUE / 1000L -> addDateRaw * 1000L
                        else -> System.currentTimeMillis()
                    }

                    val title = unescapeHtml(stripTags(rawTitle)).trim().ifBlank { url }
                    val folderPath = buildFolderPath(folderStack)
                    results += ImportedBookmark(
                        title = title,
                        url = url,
                        folder = folderPath,
                        addedAtMs = addedAtMs
                    )
                }

                else -> {
                    idx = gt + 1
                }
            }
        }

        return results
    }

    private fun findTagEnd(html: String, start: Int): Int {
        var i = start
        var quote: Char = '\u0000'
        while (i < html.length) {
            val ch = html[i]
            if (quote != '\u0000') {
                if (ch == quote) quote = '\u0000'
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

    private fun matchesTagName(header: String, name: String): Boolean {
        if (!header.startsWith(name, ignoreCase = true)) return false
        if (header.length == name.length) return true
        val next = header[name.length]
        return next.isWhitespace() || next == '/'
    }

    private fun matchesClosingTagName(header: String, name: String): Boolean {
        if (!header.startsWith('/')) return false
        val rest = header.substring(1).trimStart()
        return matchesTagName(rest, name)
    }

    /**
     * Finds the matching `</tag>` or stops safely if a structural sibling (`<DT`, `<DL`, `</DL`, `<H3`, `<A`)
     * appears first due to unclosed HTML, preventing O(N^2) scanning and bookmark swallowing.
     */
    private fun findClosingTagOrNextBlock(html: String, fromIndex: Int, tag: String): Pair<Int, Int> {
        var i = fromIndex
        val len = html.length
        while (i < len) {
            val lt = html.indexOf('<', i)
            if (lt < 0) return len to len
            val gt = findTagEnd(html, lt + 1)
            if (gt < 0) return lt to len
            val hdr = html.substring(lt + 1, gt).trim()
            if (matchesClosingTagName(hdr, tag)) {
                return lt to (gt + 1)
            }
            if (matchesTagName(hdr, "DT") ||
                matchesTagName(hdr, "DL") ||
                matchesClosingTagName(hdr, "DL") ||
                matchesTagName(hdr, "H3") ||
                matchesTagName(hdr, "A")
            ) {
                return lt to lt
            }
            i = gt + 1
        }
        return len to len
    }

    private fun stripTags(raw: String): String {
        if (!raw.contains('<')) return raw
        val sb = StringBuilder(raw.length)
        var inTag = false
        for (ch in raw) {
            if (ch == '<') {
                inTag = true
            } else if (ch == '>' && inTag) {
                inTag = false
            } else if (!inTag) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun export(bookmarks: List<BookmarkEntity>): String {
        val root = FolderNode(name = "")
        for (bm in bookmarks.sortedByDescending { it.createdAt }) {
            if (!isSupportedUrl(bm.url)) continue
            val rawSegments = bm.folder
                .split(" / ")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val segments = if (rawSegments.isNotEmpty() && rawSegments.first().equals("Bookmarks", ignoreCase = true)) {
                rawSegments.drop(1)
            } else {
                rawSegments
            }

            var current = root
            for (seg in segments) {
                current = current.subfolders.getOrPut(seg) { FolderNode(name = seg) }
            }
            current.bookmarks += bm
        }

        val sb = StringBuilder(bookmarks.size * 128 + 256)
        sb.appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        sb.appendLine("<!-- This is an automatically generated file.")
        sb.appendLine("     It will be read and overwritten.")
        sb.appendLine("     DO NOT EDIT! -->")
        sb.appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        sb.appendLine("<TITLE>Bookmarks</TITLE>")
        sb.appendLine("<H1>Bookmarks</H1>")
        sb.appendLine("<DL><p>")
        writeFolderContents(sb, root, indent = "    ")
        sb.appendLine("</DL><p>")
        return sb.toString()
    }

    private class FolderNode(
        val name: String,
        val subfolders: LinkedHashMap<String, FolderNode> = linkedMapOf(),
        val bookmarks: MutableList<BookmarkEntity> = mutableListOf()
    )

    private fun writeFolderContents(sb: StringBuilder, node: FolderNode, indent: String) {
        for ((_, child) in node.subfolders) {
            val epochSec = ((child.bookmarks.minOfOrNull { it.createdAt } ?: System.currentTimeMillis()) / 1000L)
                .coerceAtLeast(0L)
            sb.appendLine("$indent<DT><H3 ADD_DATE=\"$epochSec\">${escapeHtml(child.name)}</H3>")
            sb.appendLine("$indent<DL><p>")
            writeFolderContents(sb, child, "$indent    ")
            sb.appendLine("$indent</DL><p>")
        }
        for (bm in node.bookmarks) {
            val epochSec = (bm.createdAt / 1000L).coerceAtLeast(0L)
            sb.appendLine(
                "$indent<DT><A HREF=\"${escapeHtml(bm.url)}\" ADD_DATE=\"$epochSec\">${escapeHtml(bm.title)}</A>"
            )
        }
    }

    private fun buildFolderPath(stack: ArrayDeque<String>): String {
        if (stack.isEmpty()) return "Bookmarks"
        val trimmed = stack.toList()
        // Only strip a generic outer "Bookmarks" wrapper while preserving named root folders
        // like "Bookmarks bar" or "Bookmarks menu" so subfolder hierarchy survives Sync & re-export.
        val effective = if (trimmed.size > 1 && trimmed.first().equals("bookmarks", ignoreCase = true)) {
            trimmed.drop(1)
        } else {
            trimmed
        }
        return effective.joinToString(" / ").ifBlank { "Bookmarks" }
    }

    private fun isSupportedUrl(url: String): Boolean {
        val lower = url.trim().lowercase(Locale.ROOT)
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    internal fun escapeHtml(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    internal fun unescapeHtml(text: String): String {
        if (!text.contains('&')) return text
        return entityRegex.replace(text) { match ->
            val entity = match.groupValues[1]
            when {
                entity.equals("amp", ignoreCase = true) -> "&"
                entity.equals("lt", ignoreCase = true) -> "<"
                entity.equals("gt", ignoreCase = true) -> ">"
                entity.equals("quot", ignoreCase = true) -> "\""
                entity.equals("apos", ignoreCase = true) -> "'"
                entity.equals("nbsp", ignoreCase = true) -> " "
                entity.equals("mdash", ignoreCase = true) -> "\u2014"
                entity.equals("ndash", ignoreCase = true) -> "\u2013"
                entity.equals("rsquo", ignoreCase = true) -> "\u2019"
                entity.equals("lsquo", ignoreCase = true) -> "\u2018"
                entity.equals("rdquo", ignoreCase = true) -> "\u201D"
                entity.equals("ldquo", ignoreCase = true) -> "\u201C"
                entity.equals("hellip", ignoreCase = true) -> "\u2026"
                entity.equals("copy", ignoreCase = true) -> "\u00A9"
                entity.startsWith("#x", ignoreCase = true) -> {
                    val code = entity.substring(2).toIntOrNull(16)
                    if (isSafeUnicodeCodePoint(code)) String(Character.toChars(code!!)) else match.value
                }
                entity.startsWith("#") -> {
                    val code = entity.substring(1).toIntOrNull(10)
                    if (isSafeUnicodeCodePoint(code)) String(Character.toChars(code!!)) else match.value
                }
                else -> match.value
            }
        }
    }

    private fun isSafeUnicodeCodePoint(code: Int?): Boolean {
        if (code == null || code <= 0 || !Character.isValidCodePoint(code)) return false
        return code !in 0xD800..0xDFFF
    }
}
