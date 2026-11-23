package app.overbay.swiftfox.features.reader

data class TextRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val linkUrl: String? = null,
    val strikethrough: Boolean = false
)

sealed interface ReaderBlock {
    val id: String
    data class Heading(override val id: String, val level: Int, val text: String) : ReaderBlock
    data class Paragraph(override val id: String, val runs: List<TextRun>) : ReaderBlock
    data class Quote(override val id: String, val text: String) : ReaderBlock
    data class CodeBlock(override val id: String, val code: String) : ReaderBlock
    data class BulletItem(
        override val id: String,
        val runs: List<TextRun>,
        val orderedIndex: Int? = null,
        val indentLevel: Int = 0
    ) : ReaderBlock
    data class Image(
        override val id: String,
        val url: String,
        val alt: String? = null,
        val caption: String? = null
    ) : ReaderBlock
    data class Table(
        override val id: String,
        val headers: List<List<TextRun>>,
        val rows: List<List<List<TextRun>>>,
        val caption: String? = null
    ) : ReaderBlock
    data class DefinitionItem(
        override val id: String,
        val term: List<TextRun>,
        val definitions: List<List<TextRun>>
    ) : ReaderBlock
    data class Divider(override val id: String) : ReaderBlock
}

data class ReaderArticle(
    val url: String,
    val title: String,
    val byline: String?,
    val siteName: String?,
    val blocks: List<ReaderBlock>,
    val wordCount: Int,
    val readingTimeMinutes: Int
)
