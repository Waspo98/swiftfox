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

        val messyMetaPayload = """{"title":"  Cubs\n\tWin  ","byline":"Tab Bamford\n\t\t\t • 8 hours ago","siteName":" Bleacher \n Nation ","htmlContent":"<p>Body paragraph here.</p>","url":"https://www.bleachernation.com/post"}"""
        val normalized = extractor.parseExtractionResult(messyMetaPayload)
        assertNotNull(normalized)
        assertEquals("Cubs Win", normalized?.title)
        assertEquals("Tab Bamford • 8 hours ago", normalized?.byline)
        assertEquals("Bleacher Nation", normalized?.siteName)
    }

    @Test
    fun parsesTablesWithHeadersCaptionInlineFormattingAndColspan() {
        val html = """
            <table>
              <caption>Benchmark Results</caption>
              <thead>
                <tr><th>Engine</th><th>Score</th><th>Notes</th></tr>
              </thead>
              <tbody>
                <tr>
                  <td><strong>Swiftfox</strong></td>
                  <td><code>120Hz</code></td>
                  <td>See <a href="https://overbay.app/bench">report</a></td>
                </tr>
                <tr>
                  <td>Legacy</td>
                  <td colspan="2"><del>Deprecated</del></td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val blocks = ReaderHtmlParser.parse(html)
        assertEquals(1, blocks.size)
        val table = blocks.single() as ReaderBlock.Table
        assertEquals("Benchmark Results", table.caption)
        assertEquals(3, table.headers.size)
        assertEquals("Engine", table.headers[0].single().text)
        assertEquals("Score", table.headers[1].single().text)
        assertEquals("Notes", table.headers[2].single().text)
        assertEquals(2, table.rows.size)
        assertTrue(table.rows[0][0].single().bold)
        assertTrue(table.rows[0][1].single().code)
        assertEquals("https://overbay.app/bench", table.rows[0][2].first { it.linkUrl != null }.linkUrl)
        assertTrue(table.rows[1][1].single().strikethrough)
        assertTrue(table.rows[1][2].isEmpty())
    }

    @Test
    fun parsesFiguresImagesDefinitionListsNestedListsAndSkipsTrackingPixels() {
        val html = """
            <figure>
              <img src="https://cdn.example.com/hero.jpg" alt="Fox in snow" />
              <figcaption>Winter photo</figcaption>
            </figure>
            <p><img src="https://tracker.example.com/1x1.gif" width="1" height="1" /></p>
            <dl>
              <dt>Swiftfox</dt>
              <dd>Foldable-first Android browser</dd>
            </dl>
            <ul>
              <li><div>Parent item<ul><li>Child item</li></ul></div></li>
            </ul>
        """.trimIndent()

        val blocks = ReaderHtmlParser.parse(html)
        val image = blocks[0] as ReaderBlock.Image
        assertEquals("https://cdn.example.com/hero.jpg", image.url)
        assertEquals("Fox in snow", image.alt)
        assertEquals("Winter photo", image.caption)

        val def = blocks[1] as ReaderBlock.DefinitionItem
        assertEquals("Swiftfox", def.term.single().text)
        assertEquals("Foldable-first Android browser", def.definitions.single().single().text)

        val parentLi = blocks[2] as ReaderBlock.BulletItem
        assertEquals(0, parentLi.indentLevel)
        assertEquals("Parent item", parentLi.runs.joinToString("") { it.text })

        val childLi = blocks[3] as ReaderBlock.BulletItem
        assertEquals(1, childLi.indentLevel)
        assertEquals("Child item", childLi.runs.joinToString("") { it.text })
        assertEquals(4, blocks.size)
    }

    @Test
    fun sanitizesUnsafeLinkSchemesHandlesNestedSvgRawTagsCdataAndExpandedEntities() {
        val html = """
            <svg><svg><title>Inner SVG</title></svg><text>Outer SVG leak</text></svg>
            <script>var a = 1;</script >
            <p>
              <a href="javascript:alert(1)">Bad JS</a>
              <a href="file:///etc/passwd">Bad File</a>
              <a href="intent://scan/#Intent">Bad Intent</a>
              <a href="https://overbay.app/safe">Safe Link</a>
              20&deg;C &euro;5 &frac12; &rarr; caf&eacute; &Delta;
              <![CDATA[raw <text>]]>
            </p>
        """.trimIndent()

        val blocks = ReaderHtmlParser.parse(html)
        assertEquals(1, blocks.size)
        val p = blocks.single() as ReaderBlock.Paragraph
        val fullText = p.runs.joinToString("") { it.text }
        assertTrue(!fullText.contains("SVG"))
        assertTrue(fullText.contains("20\u00B0C \u20AC5 \u00BD \u2192 caf\u00E9 \u0394"))
        assertTrue(fullText.contains("raw <text>"))

        val links = p.runs.mapNotNull { it.linkUrl }.distinct()
        assertEquals(listOf("https://overbay.app/safe"), links)
    }

    @Test
    fun enforcesDepthCapHandlesUnquotedSelfClosingAndImageFallbacks() {
        val deepHtml = "<div>".repeat(80) + "<p>Deep content</p>" + "</div>".repeat(80)
        val deepBlocks = ReaderHtmlParser.parse(deepHtml)
        assertTrue(deepBlocks.isEmpty())

        val html = """
            <figure>
              <img src=https://cdn.example.com/unquoted.png alt=Fox/>
              <figcaption>Unquoted photo</figcaption>
            </figure>
            <p><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==" data-src="https://cdn.example.com/lazy.webp" alt="Lazy" /></p>
            <p><img src="" srcset="https://cdn.example.com/s1.jpg 1x, https://cdn.example.com/s2.jpg 2x" alt="Srcset" /></p>
        """.trimIndent()
        val images = ReaderHtmlParser.parse(html).filterIsInstance<ReaderBlock.Image>()
        assertEquals(3, images.size)
        assertEquals("https://cdn.example.com/unquoted.png", images[0].url)
        assertEquals("Fox", images[0].alt)
        assertEquals("Unquoted photo", images[0].caption)
        assertEquals("https://cdn.example.com/lazy.webp", images[1].url)
        assertEquals("https://cdn.example.com/s1.jpg", images[2].url)
    }

    @Test
    fun preservesGreekEntityCaseTracksRowspanAndEmitsMultipleFigureImages() {
        val greekParagraph = ReaderHtmlParser.parse("<p>&Delta; vs &delta;</p>")
            .single() as ReaderBlock.Paragraph
        assertEquals("\u0394 vs \u03B4", greekParagraph.runs.joinToString("") { it.text })

        val rowspanTableHtml = """
            <table>
              <thead><tr><th>Metric</th><th>Value</th></tr></thead>
              <tbody>
                <tr><td rowspan="2">Latency</td><td>12ms</td></tr>
                <tr><td>8ms</td></tr>
              </tbody>
            </table>
        """.trimIndent()
        val table = ReaderHtmlParser.parse(rowspanTableHtml).single() as ReaderBlock.Table
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Latency", "12ms"), table.rows[0].map { r -> r.joinToString("") { it.text } })
        assertEquals(listOf("", "8ms"), table.rows[1].map { r -> r.joinToString("") { it.text } })

        val galleryHtml = """
            <figure>
              <img src="https://cdn.example.com/1.jpg" alt="One" />
              <img src="https://cdn.example.com/2.jpg" alt="Two" />
              <figcaption>Two photos</figcaption>
            </figure>
        """.trimIndent()
        val galleryImages = ReaderHtmlParser.parse(galleryHtml).filterIsInstance<ReaderBlock.Image>()
        assertEquals(listOf("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"), galleryImages.map { it.url })
        assertEquals(null, galleryImages[0].caption)
        assertEquals("Two photos", galleryImages[1].caption)
    }

    @Test
    fun parsesWrappedImagesCommaSrcsetDataPlaceholderAndWindows1252Entities() {
        val html = """
            <div>
              <a href="https://example.com/gallery">
                <img src="https://cdn.example.com/linked.jpg" alt="Linked image" />
              </a>
            </div>
            <div>
              <picture>
                <source srcset="https://cdn.example.com/pic.webp" />
                <img src="https://cdn.example.com/pic.jpg" alt="Picture image" />
              </picture>
            </div>
            <p>
              <img src="" srcset="data:image/gif;base64,R0lGODlhAQABAAAAACw= 1w, https://res.cloudinary.com/demo/image/upload/w_800,c_limit,f_auto/sample.jpg 800w, https://res.cloudinary.com/demo/image/upload/w_1200,c_limit/sample.jpg 1200w" alt="Cloudinary" />
            </p>
            <p>Don&#146;t miss &#147;this&#148; &#151; only &#128;5</p>
        """.trimIndent()

        val blocks = ReaderHtmlParser.parse(html)
        val images = blocks.filterIsInstance<ReaderBlock.Image>()
        assertEquals(
            listOf(
                "https://cdn.example.com/linked.jpg",
                "https://cdn.example.com/pic.jpg",
                "https://res.cloudinary.com/demo/image/upload/w_800,c_limit,f_auto/sample.jpg"
            ),
            images.map { it.url }
        )
        assertEquals("Linked image", images[0].alt)
        assertEquals("Picture image", images[1].alt)
        assertEquals("Cloudinary", images[2].alt)

        val paragraph = blocks.filterIsInstance<ReaderBlock.Paragraph>().single()
        assertEquals(
            "Don\u2019t miss \u201Cthis\u201D \u2014 only \u20AC5",
            paragraph.runs.joinToString("") { it.text }
        )
    }
}
