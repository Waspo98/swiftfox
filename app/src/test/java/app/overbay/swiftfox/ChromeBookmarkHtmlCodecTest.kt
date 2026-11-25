package app.overbay.swiftfox

import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.features.sync.ChromeBookmarkHtmlCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeBookmarkHtmlCodecTest {

    @Test
    fun parsesChromeNetscapeHtmlWithNestedFoldersEntitiesAndFiltersUnsafeUrls() {
        val chromeExport = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
            <TITLE>Bookmarks</TITLE>
            <H1>Bookmarks</H1>
            <DL><p>
                <DT><H3 ADD_DATE="1700000000" PERSONAL_TOOLBAR_FOLDER="true">Bookmarks bar</H3>
                <DL><p>
                    <DT><A HREF="https://github.com/Waspo98/swiftfox?tab=1&amp;ref=main" ADD_DATE="1700000100">Swiftfox &amp; Android</A>
                    <DT><A HREF="javascript:alert(1)" ADD_DATE="1700000200">Bookmarklet</A>
                    <DT><H3 ADD_DATE="1700000300">Dev &quot;Tools&quot;</H3>
                    <DL><p>
                        <DT><A HREF="https://kotlinlang.org" ADD_DATE="1700000400">Kotlin &#39;Docs&#x27; &lt;2.3&gt;</A>
                    </DL><p>
                </DL><p>
                <DT><A HREF="http://192.168.1.10:8080" ADD_DATE="1700000500">Homelab</A>
            </DL><p>
        """.trimIndent()

        val parsed = ChromeBookmarkHtmlCodec.parse(chromeExport)
        assertEquals(3, parsed.size)

        assertEquals("Swiftfox & Android", parsed[0].title)
        assertEquals("https://github.com/Waspo98/swiftfox?tab=1&ref=main", parsed[0].url)
        assertEquals("Bookmarks bar", parsed[0].folder)
        assertEquals(1700000100_000L, parsed[0].addedAtMs)

        assertEquals("Kotlin 'Docs' <2.3>", parsed[1].title)
        assertEquals("https://kotlinlang.org", parsed[1].url)
        assertEquals("Bookmarks bar / Dev \"Tools\"", parsed[1].folder)

        assertEquals("Homelab", parsed[2].title)
        assertEquals("Bookmarks", parsed[2].folder)
    }

    @Test
    fun exportsAndRoundTripsFolderNamesContainingLiteralSlash() {
        val original = listOf(
            BookmarkEntity(
                id = "b-slash",
                title = "ML Paper",
                url = "https://arxiv.org/abs/1706.03762",
                folder = "Bookmarks bar / AI/ML",
                createdAt = 1710000000_000L
            )
        )
        val html = ChromeBookmarkHtmlCodec.export(original)
        assertTrue(html.contains(">AI/ML</H3>"))
        val reimported = ChromeBookmarkHtmlCodec.parse(html)
        assertEquals(1, reimported.size)
        assertEquals("Bookmarks bar / AI/ML", reimported[0].folder)
    }

    @Test
    fun exportsAndRoundTripsNestedFolderHierarchy() {
        val original = listOf(
            BookmarkEntity(
                id = "b1",
                title = "Ars Technica",
                url = "https://arstechnica.com",
                folder = "Reading / Tech",
                createdAt = 1710000000_000L
            ),
            BookmarkEntity(
                id = "b2",
                title = "Wikipedia & More",
                url = "https://en.wikipedia.org/wiki/Main_Page",
                folder = "Reading",
                createdAt = 1710000100_000L
            ),
            BookmarkEntity(
                id = "b3",
                title = "Overbay Login",
                url = "https://login.overbay.app",
                folder = "Bookmarks",
                createdAt = 1710000200_000L
            )
        )

        val html = ChromeBookmarkHtmlCodec.export(original)
        assertTrue(html.startsWith("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assertTrue(html.contains("<H3"))
        assertFalse(html.contains("Wikipedia & More")) // should be HTML escaped
        assertTrue(html.contains("Wikipedia &amp; More"))

        val reimported = ChromeBookmarkHtmlCodec.parse(html).associateBy { it.url }
        assertEquals(3, reimported.size)
        assertEquals("Reading / Tech", reimported["https://arstechnica.com"]?.folder)
        assertEquals("Reading", reimported["https://en.wikipedia.org/wiki/Main_Page"]?.folder)
        assertEquals("Wikipedia & More", reimported["https://en.wikipedia.org/wiki/Main_Page"]?.title)
        assertEquals("Bookmarks", reimported["https://login.overbay.app"]?.folder)
    }

    @Test
    fun parsesMalformedUnclosedTagsAndLargeBookmarkExportsLinearly() {
        val malformed = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            </DL></DL>
            <DL><p>
                <DT><h3 add_date='1700000000'>Single Quoted Folder</h3>
                <DL><p>
                    <DT><a href='https://example.org/one' add_date='1700000010'>One
                    <DT><a href="https://example.org/two">Two</A>
                </DL>
                <DT><H3>Unclosed HeadingWithoutEndTag
            </DL>
        """.trimIndent()

        val malformedParsed = ChromeBookmarkHtmlCodec.parse(malformed)
        assertEquals(2, malformedParsed.size)
        assertEquals("https://example.org/one", malformedParsed[0].url)
        assertEquals("One", malformedParsed[0].title)
        assertEquals("Single Quoted Folder", malformedParsed[0].folder)
        assertEquals("https://example.org/two", malformedParsed[1].url)
        assertEquals("Two", malformedParsed[1].title)

        val largeInput = (1..1500).map { i ->
            BookmarkEntity(
                id = "bm-$i",
                title = "Bookmark #$i & Notes",
                url = "https://docs.overbay.app/page/$i",
                folder = "Engineering / Subfolder ${i % 12}",
                createdAt = 1710000000_000L + i * 1000L
            )
        }
        val exported = ChromeBookmarkHtmlCodec.export(largeInput)
        val parsedLarge = ChromeBookmarkHtmlCodec.parse(exported)
        assertEquals(1500, parsedLarge.size)
    }

    @Test
    fun canonicalizesSchemeAndHostCaseWhilePreservingPathAndQueryCase() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
                <DT><A HREF="HTTPS://GitHub.COM/Waspo98/Swiftfox?Tab=Code#Top">Swiftfox Repo</A>
                <DT><A HREF="HTTP://Example.ORG:8080/MixedCase/Path">Local Doc</A>
            </DL>
        """.trimIndent()

        val parsed = ChromeBookmarkHtmlCodec.parse(html)
        assertEquals(2, parsed.size)
        assertEquals("https://github.com/Waspo98/Swiftfox?Tab=Code#Top", parsed[0].url)
        assertEquals("http://example.org:8080/MixedCase/Path", parsed[1].url)
    }
}
