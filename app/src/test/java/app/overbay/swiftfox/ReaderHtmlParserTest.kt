package app.overbay.swiftfox

import app.overbay.swiftfox.features.reader.ReaderBlock
import app.overbay.swiftfox.features.reader.ReaderExtractor
import app.overbay.swiftfox.features.reader.ReaderHtmlParser
import app.overbay.swiftfox.features.reader.TextRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHtmlParserTest {

    @Test
    fun parsesHeadingsAndDivider() {
        val html = """
            <h1>Main Title</h1>
            <h2>Section Heading</h2>
            <h3>Subsection</h3>
            <hr />
            <h4>Detail Header</h4>
        """.trimIndent()

        val blocks = ReaderHtmlParser.parse(html)
        assertEquals(5, blocks.size)

        val h1 = blocks[0] as ReaderBlock.Heading
        assertEquals(1, h1.level)
        assertEquals("Main Title", h1.text)

        val h2 = blocks[1] as ReaderBlock.Heading
        assertEquals(2, h2.level)
        assertEquals("Section Heading", h2.text)

        val h3 = blocks[2] as ReaderBlock.Heading
        assertEquals(3, h3.level)
        assertEquals("Subsection", h3.text)

        assertTrue(blocks[3] is ReaderBlock.Divider)

        val h4 = blocks[4] as ReaderBlock.Heading
        assertEquals(4, h4.level)
        assertEquals("Detail Header", h4.text)
    }

    @Test
    fun parsesInlineFormattingAndLinksAndLineBreaks() {
        val html = """
            <p>
              Read <strong>bold</strong>, <b>also bold</b>, <em>italic</em>,
              <i>also italic</i>, and <code>inlineCode()</code>.<br/>
              Visit <a href="https://overbay.app/docs?a=1&amp;b=2">Swiftfox <strong>Docs</strong></a> now.
            </p>
        """.trimIndent()

        val blocks = ReaderHtmlParser.parse(html)
        assertEquals(1, blocks.size)

        val paragraph = blocks.single() as ReaderBlock.Paragraph
        assertEquals(
            listOf(
                TextRun("Read "),
                TextRun("bold", bold = true),
                TextRun(", "),
                TextRun("also bold", bold = true),
                TextRun(", "),
                TextRun("italic", italic = true),
                TextRun(", "),
                TextRun("also italic", italic = true),
                TextRun(", and "),
                TextRun("inlineCode()", code = true),
                TextRun(".\nVisit "),
                TextRun("Swiftfox ", linkUrl = "https://overbay.app/docs?a=1&b=2"),
                TextRun("Docs", bold = true, linkUrl = "https://overbay.app/docs?a=1&b=2"),
                TextRun(" now.")
            ),
            paragraph.runs
        )
    }

    @Test
    fun parsesUnorderedAndOrderedLists() {
        val html = """
            <ul>
              <li>First bullet</li>
              <li>Second <em>styled</em> bullet</li>
            </ul>
            <ol>
              <li>Step one</li>
              <li>Step two</li>
            </ol>
            <ol start="5">
              <li>Step five</li>
            </ol>
        """.trimIndent()

        val blocks = ReaderHtmlParser.parse(html)
        assertEquals(5, blocks.size)

        val ul1 = blocks[0] as ReaderBlock.BulletItem
        assertNull(ul1.orderedIndex)
        assertEquals(listOf(TextRun("First bullet")), ul1.runs)

        val ul2 = blocks[1] as ReaderBlock.BulletItem
        assertNull(ul2.orderedIndex)
        assertEquals(
            listOf(TextRun("Second "), TextRun("styled", italic = true), TextRun(" bullet")),
            ul2.runs
        )

        val ol1 = blocks[2] as ReaderBlock.BulletItem
        assertEquals(1, ol1.orderedIndex)
        assertEquals(listOf(TextRun("Step one")), ol1.runs)

        val ol2 = blocks[3] as ReaderBlock.BulletItem
        assertEquals(2, ol2.orderedIndex)
        assertEquals(listOf(TextRun("Step two")), ol2.runs)

        val ol5 = blocks[4] as ReaderBlock.BulletItem
        assertEquals(5, ol5.orderedIndex)
        assertEquals(listOf(TextRun("Step five")), ol5.runs)
    }

    @Test
    fun parsesQuotesAndPreformattedCodeBlocks() {
        val html = """
            <blockquote>
              <p>Simplicity is prerequisite for reliability.</p>
              <p>Edsger W. Dijkstra</p>
            </blockquote>
            <pre><code class="language-kotlin">fun greet(name: String) {
    println("Hello &lt;" + name + "&gt;")
}</code></pre>
        """.trimIndent()

        val blocks = ReaderHtmlParser.parse(html)
        assertEquals(2, blocks.size)

        val quote = blocks[0] as ReaderBlock.Quote
        assertEquals(
            "Simplicity is prerequisite for reliability.\n\nEdsger W. Dijkstra",
            quote.text
        )

        val codeBlock = blocks[1] as ReaderBlock.CodeBlock
        assertEquals(
            "fun greet(name: String) {\n    println(\"Hello <\" + name + \">\")\n}",
            codeBlock.code
        )
    }

    @Test
    fun decodesNamedNumericAndHexEntities() {
        val html = """
            <p>
              &amp; &lt;tag&gt; &quot;quoted&quot; &#39;single&#39; &apos;alt&apos;
              word1&nbsp;&nbsp;&nbsp;word2 &mdash; &ndash; &hellip; &#123; &#x1F4;
            </p>
        """.trimIndent()

        val blocks = ReaderHtmlParser.parse(html)
        assertEquals(1, blocks.size)
        val text = (blocks.single() as ReaderBlock.Paragraph).runs.joinToString("") { it.text }
        assertEquals(
            "& <tag> \"quoted\" 'single' 'alt' word1 word2 \u2014 \u2013 \u2026 { \u01F4",
            text
        )
    }

    @Test
    fun handlesEmptyAndPlainTextFallback() {
        assertEquals(emptyList<ReaderBlock>(), ReaderHtmlParser.parse(""))
        assertEquals(emptyList<ReaderBlock>(), ReaderHtmlParser.parse("   \n\t  "))
        assertEquals(emptyList<ReaderBlock>(), ReaderHtmlParser.parse("<p>  &nbsp; </p>"))

        val plainFallback = ReaderHtmlParser.parse("First   plain\nparagraph.\n\nSecond   plain\tparagraph.")
        assertEquals(2, plainFallback.size)
        assertEquals(
            listOf(TextRun("First plain paragraph.")),
            (plainFallback[0] as ReaderBlock.Paragraph).runs
        )
        assertEquals(
            listOf(TextRun("Second plain paragraph.")),
            (plainFallback[1] as ReaderBlock.Paragraph).runs
        )

        val inlineOnlyFallback = ReaderHtmlParser.parse("<span>Wrapped   text &amp; more</span>")
        assertEquals(1, inlineOnlyFallback.size)
        assertEquals(
            listOf(TextRun("Wrapped text & more")),
            (inlineOnlyFallback[0] as ReaderBlock.Paragraph).runs
        )
    }

    @Test
    fun readerExtractorUnwrapsWebViewJsonStringLiteral() {
        val extractor = ReaderExtractor(context = null)
        assertNull(extractor.parseExtractionResult(null))
        assertNull(extractor.parseExtractionResult("null"))
        assertNull(extractor.parseExtractionResult("\"null\""))

        val wrappedPayload = "\"{\\\"title\\\":\\\"Kotlin News\\\",\\\"byline\\\":\\\"Jane Doe\\\",\\\"siteName\\\":\\\"Overbay\\\",\\\"excerpt\\\":\\\"Summary\\\",\\\"htmlContent\\\":\\\"<h2>Heading</h2><p>Hello <strong>world</strong>!</p>\\\",\\\"wordCount\\\":3,\\\"readingTimeMinutes\\\":1,\\\"url\\\":\\\"https://overbay.app/post\\\"}\""
        val article = extractor.parseExtractionResult(wrappedPayload)
        assertNotNull(article)
        assertEquals("Kotlin News", article?.title)
        assertEquals("Jane Doe", article?.byline)
        assertEquals("Overbay", article?.siteName)
        assertEquals("https://overbay.app/post", article?.url)
        assertEquals(3, article?.wordCount)
        assertEquals(1, article?.readingTimeMinutes)
        assertEquals(2, article?.blocks?.size)
        assertTrue(article?.blocks?.get(0) is ReaderBlock.Heading)
        assertTrue(article?.blocks?.get(1) is ReaderBlock.Paragraph)
    }
}
