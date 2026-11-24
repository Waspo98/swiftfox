package app.overbay.swiftfox

import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.SearchEngineId
import app.overbay.swiftfox.features.omnibox.AutocompleteCandidate
import app.overbay.swiftfox.features.omnibox.OmniboxAutocompleteGate
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertEquals("http://192.168.1.10:8080", OmniboxEngine.resolveInput("192.168.1.10:8080", settings))
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
        assertTrue(OmniboxEngine.isLocalHostOrLan("192.168.1.10"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("localhost"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("10.8.0.1"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("127.0.0.2"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("[::1]"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("[::ffff:192.168.1.10]"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("100.64.12.3"))
        assertTrue(OmniboxEngine.isLocalHostOrLan("nas"))
        assertFalse(OmniboxEngine.isLocalHostOrLan("10.attacker.com"))
        assertFalse(OmniboxEngine.isLocalHostOrLan("172.16.example.org"))
        assertFalse(OmniboxEngine.isLocalHostOrLan(""))
        assertFalse(OmniboxEngine.isLocalHostOrLan("overbay.app"))
        assertFalse(OmniboxEngine.isLocalHostOrLan("google.com"))

        assertTrue(OmniboxEngine.isLocalOrPrivateInetAddress(java.net.InetAddress.getByName("127.0.0.1")))
        assertTrue(OmniboxEngine.isLocalOrPrivateInetAddress(java.net.InetAddress.getByName("192.168.1.10")))
        assertTrue(OmniboxEngine.isLocalOrPrivateInetAddress(java.net.InetAddress.getByName("169.254.10.20")))
        assertTrue(OmniboxEngine.isLocalOrPrivateInetAddress(java.net.InetAddress.getByName("fc00::1")))
        assertFalse(OmniboxEngine.isLocalOrPrivateInetAddress(java.net.InetAddress.getByName("93.184.216.34")))
    }

    @Test
    fun resolveInputLowercasesHostWhilePreservingPathCase() {
        val settings = BrowserSettings()
        assertEquals("https://overbay.app", OmniboxEngine.resolveInput("Overbay.app", settings))
        assertEquals("https://overbay.app/Swiftfox/Docs", OmniboxEngine.resolveInput("Overbay.app/Swiftfox/Docs", settings))
    }

    @Test
    fun inlineAutocompleteMatchesDomainsAndPathsWithPriorityAndFrecency() {
        val candidates = listOf(
            AutocompleteCandidate(
                url = "https://www.bleachernation.com/cubs/2026/11/24/article-one/",
                priority = 10,
                visitCount = 5,
                lastVisitedAt = 1000L
            ),
            AutocompleteCandidate(
                url = "https://www.bleachernation.com/bears/2026/11/24/article-two/",
                priority = 10,
                visitCount = 4,
                lastVisitedAt = 2000L
            ),
            AutocompleteCandidate(
                url = "https://overbay.app/swiftfox/",
                priority = 20,
                visitCount = 1,
                lastVisitedAt = 500L
            ),
            AutocompleteCandidate(
                url = "https://github.com/Waspo98/swiftfox",
                priority = 40,
                visitCount = 1,
                lastVisitedAt = 3000L
            )
        )

        val bn = OmniboxEngine.findInlineAutocomplete("bleacherna", candidates)
        assertNotNull(bn)
        assertEquals("bleacherna", bn?.typedPrefix)
        assertEquals("tion.com", bn?.completionSuffix)
        assertEquals("bleachernation.com", bn?.displayText)
        assertEquals("https://www.bleachernation.com/", bn?.targetUrl)

        val bnWww = OmniboxEngine.findInlineAutocomplete("www.bleach", candidates)
        assertNotNull(bnWww)
        assertEquals("ernation.com", bnWww?.completionSuffix)
        assertEquals("www.bleachernation.com", bnWww?.displayText)
        assertEquals("https://www.bleachernation.com/", bnWww?.targetUrl)

        val overbay = OmniboxEngine.findInlineAutocomplete("Overb", candidates)
        assertNotNull(overbay)
        assertEquals("Overb", overbay?.typedPrefix)
        assertEquals("ay.app", overbay?.completionSuffix)
        assertEquals("Overbay.app", overbay?.displayText)
        assertEquals("https://overbay.app/", overbay?.targetUrl)

        val pathSeg = OmniboxEngine.findInlineAutocomplete("github.com/Wa", candidates)
        assertNotNull(pathSeg)
        assertEquals("spo98/", pathSeg?.completionSuffix)
        assertEquals("https://github.com/Waspo98/", pathSeg?.targetUrl)

        val deepPath = OmniboxEngine.findInlineAutocomplete("github.com/Waspo98/sw", candidates)
        assertNotNull(deepPath)
        assertEquals("iftfox", deepPath?.completionSuffix)
        assertEquals("https://github.com/Waspo98/swiftfox", deepPath?.targetUrl)
    }

    @Test
    fun inlineAutocompleteSuppressesSpacesBangsSingleCharAndExactHost() {
        val candidates = listOf(
            AutocompleteCandidate(url = "https://www.bleachernation.com/", priority = 10, visitCount = 10)
        )
        assertNull(OmniboxEngine.findInlineAutocomplete("b", candidates))
        assertNull(OmniboxEngine.findInlineAutocomplete("bleacherna ", candidates))
        assertNull(OmniboxEngine.findInlineAutocomplete("bleacher nation", candidates))
        assertNull(OmniboxEngine.findInlineAutocomplete("!g", candidates))
        assertNull(OmniboxEngine.findInlineAutocomplete("bleachernation.com", candidates))
        assertNull(OmniboxEngine.findInlineAutocomplete("https://", candidates))
    }

    @Test
    fun inlineAutocompleteDoesNotUpgradeLocalIpv6AndDeduplicatesVisits() {
        val ipv6Local = listOf(
            AutocompleteCandidate(url = "http://[::1]:8080/admin", priority = 10, visitCount = 5)
        )
        assertNull(OmniboxEngine.findInlineAutocomplete("https://[::", ipv6Local))
        val httpIpv6 = OmniboxEngine.findInlineAutocomplete("http://[::", ipv6Local)
        assertNotNull(httpIpv6)
        assertEquals("http://[::1]:8080/", httpIpv6?.targetUrl)

        // Duplicate entries from in-memory state + Room search must not double-count visits
        val duplicatedCandidates = listOf(
            AutocompleteCandidate(url = "https://alpha.example.com/", priority = 10, visitCount = 6, lastVisitedAt = 100L),
            AutocompleteCandidate(url = "https://alpha.example.com/", priority = 10, visitCount = 6, lastVisitedAt = 100L),
            AutocompleteCandidate(url = "https://albatross.example.com/", priority = 10, visitCount = 9, lastVisitedAt = 100L)
        )
        val best = OmniboxEngine.findInlineAutocomplete("al", duplicatedCandidates)
        assertNotNull(best)
        assertEquals("https://albatross.example.com/", best?.targetUrl)
    }

    @Test
    fun autocompleteGateDismissesSuffixOnBackspaceWithoutTrappingPartialSearches() {
        val gate = OmniboxAutocompleteGate(initialSuppressed = false)
        assertFalse(gate.isSuppressed)

        // Typing "bleach" keeps autocomplete enabled
        gate.onUserTextCommitted("bleac", "bleach")
        assertFalse(gate.isSuppressed)

        // Pressing Backspace while completion is highlighted intercepts deletion and suppresses "bleach"
        val intercepted = gate.interceptBackspaceToDismiss(
            originalText = "bleach",
            proposedText = "bleac",
            proposedSelectionCollapsedAtEnd = true,
            hasActiveAutocomplete = true
        )
        assertTrue(intercepted)
        assertTrue(gate.isSuppressed)
        assertEquals("bleach", gate.dismissedPrefix)

        // Pressing Backspace again (no active completion) deletes normally and retyping 'h' stays suppressed
        assertFalse(
            gate.interceptBackspaceToDismiss(
                originalText = "bleach",
                proposedText = "bleac",
                proposedSelectionCollapsedAtEnd = true,
                hasActiveAutocomplete = false
            )
        )
        gate.onUserTextCommitted("bleach", "bleac")
        assertTrue(gate.isSuppressed)
        gate.onUserTextCommitted("bleac", "bleach")
        assertTrue(gate.isSuppressed)

        // Typing further past the dismissed prefix re-enables autocomplete
        gate.onUserTextCommitted("bleach", "bleache")
        assertFalse(gate.isSuppressed)
        assertNull(gate.dismissedPrefix)

        // Clearing resets suppression
        gate.onCleared()
        assertFalse(gate.isSuppressed)
    }
}
