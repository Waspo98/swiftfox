package app.overbay.swiftfox

import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.SearchEngineId
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniboxEngineTest {

    @Test
    fun resolvesDefaultGoogleSearch() {
        val settings = BrowserSettings(searchEngine = SearchEngineId.GOOGLE)
        val url = OmniboxEngine.resolveInput("kotlin compose foldable", settings)
        assertEquals("https://www.google.com/search?q=kotlin+compose+foldable", url)
    }

    @Test
    fun resolvesCustomSearchEngineAndFallsBackWhenInvalid() {
        val customValid = BrowserSettings(
            searchEngine = SearchEngineId.CUSTOM,
            customSearchUrlTemplate = "https://kagi.com/search?q=%s"
        )
        assertEquals(
            "https://kagi.com/search?q=swiftfox+browser",
            OmniboxEngine.resolveInput("swiftfox browser", customValid)
        )

        val customMissingPlaceholder = BrowserSettings(
            searchEngine = SearchEngineId.CUSTOM,
            customSearchUrlTemplate = "https://kagi.com/search?q="
        )
        assertEquals(
            "https://www.google.com/search?q=swiftfox",
            OmniboxEngine.resolveInput("swiftfox", customMissingPlaceholder)
        )

        val customNonHttpScheme = BrowserSettings(
            searchEngine = SearchEngineId.CUSTOM,
            customSearchUrlTemplate = "javascript:alert(%s)"
        )
        assertEquals(
            "https://www.google.com/search?q=swiftfox",
            OmniboxEngine.resolveInput("swiftfox", customNonHttpScheme)
        )
    }

    @Test
    fun resolvesDirectDomainAndLanIp() {
        val settings = BrowserSettings()
        assertEquals("https://github.com/overbay", OmniboxEngine.resolveInput("github.com/overbay", settings))
        assertEquals("https://example.com?q=1", OmniboxEngine.resolveInput("example.com?q=1", settings))
        assertEquals("https://example.com#section", OmniboxEngine.resolveInput("example.com#section", settings))
        assertEquals("http://192.168.86.200:8080", OmniboxEngine.resolveInput("192.168.86.200:8080", settings))
        assertEquals("http://localhost:3000", OmniboxEngine.resolveInput("localhost:3000", settings))
        assertEquals("http://[::1]:8080", OmniboxEngine.resolveInput("[::1]:8080", settings))
    }

    @Test
    fun treatsDecimalsFilenamesAndInvalidIpsAsSearches() {
        val settings = BrowserSettings(searchEngine = SearchEngineId.GOOGLE)
        assertFalse(OmniboxEngine.looksLikeUrl("3.14"))
        assertFalse(OmniboxEngine.looksLikeUrl("v1.2"))
        assertFalse(OmniboxEngine.looksLikeUrl("node.js"))
        assertFalse(OmniboxEngine.looksLikeUrl("index.html"))
        assertFalse(OmniboxEngine.looksLikeUrl("999.999.999.999"))
        assertEquals("https://www.google.com/search?q=3.14", OmniboxEngine.resolveInput("3.14", settings))
        assertEquals("https://www.google.com/search?q=node.js", OmniboxEngine.resolveInput("node.js", settings))
        assertEquals(
            "https://www.google.com/search?q=node.js",
            OmniboxEngine.buildSearchUrl("node.js", settings)
        )
    }

    @Test
    fun resolvesKeywordBangsAndBareBangHomepages() {
        val settings = BrowserSettings()
        val wiki = OmniboxEngine.resolveInput("!w Android WebView", settings)
        assertEquals("https://en.wikipedia.org/w/index.php?search=Android+WebView", wiki)

        val yt = OmniboxEngine.resolveInput("@yt 120hz testufo", settings)
        assertEquals("https://www.youtube.com/results?search_query=120hz+testufo", yt)

        val bareYt = OmniboxEngine.resolveInput("!yt", settings)
        assertEquals("https://www.youtube.com/", bareYt)

        val detected = OmniboxEngine.detectBang("!gh swiftfox")
        assertNotNull(detected)
        assertEquals("GitHub", detected?.bang?.name)
        assertEquals("swiftfox", detected?.query)
    }

    @Test
    fun normalizesHostsAndExtractsWithSpecialCharacters() {
        assertEquals("example.com", OmniboxEngine.normalizeHost("WWW.Example.COM"))
        assertEquals("example.com", OmniboxEngine.extractHost("https://www.example.com/path with spaces?a=1"))
        assertEquals("::1", OmniboxEngine.extractHost("http://[::1]:8080/dashboard"))
        assertEquals("evil.com", OmniboxEngine.extractHost("https://evil.com\\@good.com/path"))
    }

    @Test
    fun classifiesLanVsPublicHosts() {
        assertTrue(OmniboxEngine.isLocalHostOrLan("192.168.86.200"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("localhost"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("10.8.0.1"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("127.0.0.2"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("[::1]"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("[::ffff:192.168.86.200]"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("100.64.12.3"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("nas"))
        assertFalse(OmniboxEngine.isLocalHostOrLan("10.attacker.com"))
        assertFalse(OmniboxEngine.isLocalHostOrLan("172.16.example.org"))
        assertFalse(OmniboxEngine.isLocalHostOrLan(""))
        assertFalse(OmniboxEngine.isLocalHostOrLan("overbay.app"))
        assertFalse(OmniboxEngine.isLocalHostOrLan("google.com"))

        assertTrue(OmniboxEngine.isLocalOrPrivateInetAddress(java.net.InetAddress.getByName("127.0.0.1")))
        assertTrue(OmniboxEngine.isLocalOrPrivateInetAddress(java.net.InetAddress.getByName("192.168.86.200")))
        assertTrue(OmniboxEngine.isLocalOrPrivateInetAddress(java.net.InetAddress.getByName("169.254.10.20")))
        assertTrue(OmniboxEngine.isLocalOrPrivateInetAddress(java.net.InetAddress.getByName("fc00::1")))
        assertFalse(OmniboxEngine.isLocalOrPrivateInetAddress(java.net.InetAddress.getByName("93.184.216.34")))
    }
}
