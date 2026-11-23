package app.overbay.swiftfox.features.sync

import app.overbay.swiftfox.data.db.BookmarkEntity
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

    private val tokenRegex = Regex(
        """<H3\b[^>]*>.*?</H3>|<A\b[^>]*>.*?</A>|<DL\b[^>]*>|</DL>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val h3Regex = Regex("""<H3\b[^>]*>(.*?)</H3>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val anchorRegex = Regex("""<A\b([^>]*)>(.*?)</A>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val dlOpenRegex = Regex("""<DL\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val dlCloseRegex = Regex("""</DL>""", RegexOption.IGNORE_CASE)
    private val hrefAttrRegex = Regex("""\bHREF\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
    private val addDateAttrRegex = Regex("""\bADD_DATE\s*=\s*(?:"(\d+)"|'(\d+)'|(\d+))""", RegexOption.IGNORE_CASE)
    private val entityRegex = Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""")

    fun parse(html: String): List<ImportedBookmark> {
        if (html.isBlank()) return emptyList()

        val results = mutableListOf<ImportedBookmark>()
        val folderStack = ArrayDeque<String>()
        var pendingFolderName: String? = null
        var seenRootDl = false

        for (match in tokenRegex.findAll(html)) {
            val token = match.value
            when {
                dlOpenRegex.matches(token) -> {
                    if (!seenRootDl) {
                        seenRootDl = true
                        pendingFolderName = null
                    } else {
                        folderStack.addLast(pendingFolderName ?: "Bookmarks")
                        pendingFolderName = null
                    }
                }

                dlCloseRegex.matches(token) -> {
                    pendingFolderName = null
                    if (folderStack.isNotEmpty()) {
                        folderStack.removeLast()
                    }
                }

                token.startsWith("<H3", ignoreCase = true) -> {
                    val rawName = h3Regex.find(token)?.groupValues?.getOrNull(1).orEmpty()
                    val cleaned = unescapeHtml(rawName).trim()
                    pendingFolderName = cleaned.takeIf { it.isNotEmpty() }
                }

                else -> {
                    // Stray <H3> without an immediately following <DL> must not attach to a later <DL>
                    pendingFolderName = null
                    val anchorMatch = anchorRegex.find(token) ?: continue
                    val attrs = anchorMatch.groupValues[1]
                    val rawTitle = anchorMatch.groupValues[2]
                    val hrefMatch = hrefAttrRegex.find(attrs) ?: continue
                    val rawHref = hrefMatch.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty()
                    val url = unescapeHtml(rawHref).trim()
                    if (!isSupportedUrl(url)) continue

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

                    val title = unescapeHtml(rawTitle).trim().ifBlank { url }
                    val folderPath = buildFolderPath(folderStack)
                    results += ImportedBookmark(
                        title = title,
                        url = url,
                        folder = folderPath,
                        addedAtMs = addedAtMs
                    )
                }
            }
        }

        return results
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

        val sb = StringBuilder()
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
