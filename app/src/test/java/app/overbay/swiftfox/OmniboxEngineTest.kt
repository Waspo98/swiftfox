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
    fun resolvesDirectDomainAndLanIp() {
        val settings = BrowserSettings()
        assertEquals("https://github.com/overbay", OmniboxEngine.resolveInput("github.com/overbay", settings))
        assertEquals("http://192.168.86.200:8080", OmniboxEngine.resolveInput("192.168.86.200:8080", settings))
        assertEquals("http://localhost:3000", OmniboxEngine.resolveInput("localhost:3000", settings))
    }

    @Test
    fun resolvesKeywordBangs() {
        val settings = BrowserSettings()
        val wiki = OmniboxEngine.resolveInput("!w Android WebView", settings)
        assertEquals("https://en.wikipedia.org/w/index.php?search=Android+WebView", wiki)

        val yt = OmniboxEngine.resolveInput("@yt 120hz testufo", settings)
        assertEquals("https://www.youtube.com/results?search_query=120hz+testufo", yt)

        val detected = OmniboxEngine.detectBang("!gh swiftfox")
        assertNotNull(detected)
        assertEquals("GitHub", detected?.bang?.name)
        assertEquals("swiftfox", detected?.query)
    }

    @Test
    fun classifiesLanVsPublicHosts() {
        assertTrue(OmniboxEngine.isLocalHostOrLan("192.168.86.200"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("localhost"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("10.8.0.1"))
        assertFalse(OmniboxEngine.isLocalHostOrLan("overbay.app"))
        assertFalse(OmniboxEngine.isLocalHostOrLan("google.com"))
    }
}
