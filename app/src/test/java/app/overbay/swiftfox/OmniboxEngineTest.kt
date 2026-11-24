package app.overbay.swiftfox

import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.HistoryEntity
import app.overbay.swiftfox.data.db.SyncedRemoteTabEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.SearchEngineId
import app.overbay.swiftfox.features.omnibox.AutocompleteCandidate
import app.overbay.swiftfox.features.omnibox.OmniboxAutocompleteGate
import app.overbay.swiftfox.features.omnibox.OmniboxCandidateBuilder
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.OmniboxSuggestion
import app.overbay.swiftfox.features.omnibox.SuggestionKind
import app.overbay.swiftfox.features.omnibox.TopDomainsRepository
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun inlineAutocompleteSuppressesSpacesBangsWeakSingleCharAndExactHost() {
        val frequentPersonal = listOf(
            AutocompleteCandidate(
                url = "https://www.bleachernation.com/",
                priority = OmniboxEngine.PRIORITY_HISTORY,
                visitCount = 10
            )
        )
        val weakHistory = listOf(
            AutocompleteCandidate(
                url = "https://www.bleachernation.com/",
                priority = OmniboxEngine.PRIORITY_HISTORY,
                visitCount = 1
            )
        )
        // High-frequency personal site completes on 1 character; single-visit history waits for 2+
        assertEquals("bleachernation.com", OmniboxEngine.findInlineAutocomplete("b", frequentPersonal)?.displayText)
        assertNull(OmniboxEngine.findInlineAutocomplete("b", weakHistory))
        assertEquals("bleachernation.com", OmniboxEngine.findInlineAutocomplete("bl", weakHistory)?.displayText)

        assertNull(OmniboxEngine.findInlineAutocomplete("bleacherna ", frequentPersonal))
        assertNull(OmniboxEngine.findInlineAutocomplete("bleacher nation", frequentPersonal))
        assertNull(OmniboxEngine.findInlineAutocomplete("!g", frequentPersonal))
        assertNull(OmniboxEngine.findInlineAutocomplete("bleachernation.com", frequentPersonal))
        assertNull(OmniboxEngine.findInlineAutocomplete("https://", frequentPersonal))
        assertNull(OmniboxEngine.findInlineAutocomplete("www.", frequentPersonal))
    }

    @Test
    fun topDomainsAssetCoversTheVergeAndRanksByPopularity() {
        val assetFile = listOf(
            File("src/main/assets/omnibox/top_domains.txt"),
            File("app/src/main/assets/omnibox/top_domains.txt")
        ).firstOrNull { it.isFile }
        assertNotNull("Expected bundled top_domains.txt asset to exist", assetFile)

        val repo = assetFile!!.bufferedReader(Charsets.UTF_8).use { reader ->
            TopDomainsRepository.fromLines(reader.lineSequence())
        }
        assertTrue("Expected rich top domains corpus", repo.size >= 500)

        // Exact scenario from user report: typing "theverge" or "thev" completes to theverge.com
        val theVergeExact = OmniboxEngine.findInlineAutocomplete(
            "theverge",
            repo.findPrefixCandidates("theverge")
        )
        assertNotNull(theVergeExact)
        assertEquals(".com", theVergeExact?.completionSuffix)
        assertEquals("theverge.com", theVergeExact?.displayText)
        assertEquals("https://theverge.com/", theVergeExact?.targetUrl)

        val theVergePrefix = OmniboxEngine.findInlineAutocomplete(
            "thev",
            repo.findPrefixCandidates("thev")
        )
        assertEquals("theverge.com", theVergePrefix?.displayText)

        val theVergeDot = OmniboxEngine.findInlineAutocomplete(
            "theverge.",
            repo.findPrefixCandidates("theverge.")
        )
        assertEquals("com", theVergeDot?.completionSuffix)
        assertEquals("theverge.com", theVergeDot?.displayText)

        val theVergeWww = OmniboxEngine.findInlineAutocomplete(
            "www.thev",
            repo.findPrefixCandidates("www.thev")
        )
        assertEquals("www.theverge.com", theVergeWww?.displayText)
        assertEquals("https://theverge.com/", theVergeWww?.targetUrl)

        // Popularity rank breaks prefix ties cleanly
        assertEquals(
            "amazon.com",
            OmniboxEngine.findInlineAutocomplete("ama", repo.findPrefixCandidates("ama"))?.displayText
        )
        assertEquals(
            "reddit.com",
            OmniboxEngine.findInlineAutocomplete("red", repo.findPrefixCandidates("red"))?.displayText
        )
        assertEquals(
            "github.com",
            OmniboxEngine.findInlineAutocomplete("git", repo.findPrefixCandidates("git"))?.displayText
        )

        // 1-2 character prefixes without a dot stay free of top-domain hijacking
        assertTrue(repo.findPrefixCandidates("th").isEmpty())
        assertTrue(repo.findPrefixCandidates("go").isEmpty())
        assertTrue(repo.findPrefixCandidates("http://thev").isEmpty())

        // Explicit dot allows 2-char domain completion (e.g. "x.")
        val xDot = OmniboxEngine.findInlineAutocomplete("x.", repo.findPrefixCandidates("x."))
        assertEquals("x.com", xDot?.displayText)
    }

    @Test
    fun personalHistoryAlwaysOutranksTopDomainsWithoutFallbackScorePollution() {
        val candidates = listOf(
            // User visited theathletic.com 5 times
            AutocompleteCandidate(
                url = "https://theathletic.com/",
                priority = OmniboxEngine.PRIORITY_HISTORY,
                visitCount = 5,
                lastVisitedAt = 1000L
            ),
            // User visited theverge.com only 1 time, and it ALSO has a high bundled top-domain score
            AutocompleteCandidate(
                url = "https://theverge.com/",
                priority = OmniboxEngine.PRIORITY_HISTORY,
                visitCount = 1,
                lastVisitedAt = 1000L
            ),
            AutocompleteCandidate(
                url = "https://theverge.com/",
                priority = OmniboxEngine.PRIORITY_TOP_DOMAIN,
                visitCount = 99_968,
                lastVisitedAt = 0L
            )
        )
        val best = OmniboxEngine.findInlineAutocomplete("the", candidates)
        assertNotNull(best)
        assertEquals("theathletic.com", best?.displayText)
    }

    @Test
    fun inlineAutocompleteNormalizesMobileSubdomainsWhileRespectingExplicitMobilePrefix() {
        val candidates = listOf(
            AutocompleteCandidate(
                url = "https://m.youtube.com/watch?v=abc",
                priority = OmniboxEngine.PRIORITY_HISTORY,
                visitCount = 6,
                lastVisitedAt = 5000L
            ),
            AutocompleteCandidate(
                url = "https://m.co/",
                priority = OmniboxEngine.PRIORITY_HISTORY,
                visitCount = 4,
                lastVisitedAt = 2000L
            )
        )
        val ytSingle = OmniboxEngine.findInlineAutocomplete("y", candidates)
        assertNotNull(ytSingle)
        assertEquals("youtube.com", ytSingle?.displayText)
        assertEquals("https://youtube.com/", ytSingle?.targetUrl)

        val ytExplicitMobile = OmniboxEngine.findInlineAutocomplete("m.you", candidates)
        assertNotNull(ytExplicitMobile)
        assertEquals("m.youtube.com", ytExplicitMobile?.displayText)
        assertEquals("https://m.youtube.com/", ytExplicitMobile?.targetUrl)

        // m.co must not strip its single label into invalid "co"
        assertNull(OmniboxEngine.findInlineAutocomplete("co", candidates))
        assertEquals("m.co", OmniboxEngine.findInlineAutocomplete("m.c", candidates)?.displayText)
        assertEquals("m.youtube.com", OmniboxEngine.findInlineAutocomplete("m.", candidates)?.displayText)
    }

    @Test
    fun parsesGoogleChromeNavigationalSuggestionsAndStandardOpenSearchArrays() {
        val chromePayload = """
            [
              "bleachernation",
              ["bleacher nation","bleachernation.con","http://bleachernation.com","http://192.168.1.1/admin","bleacher nation cubs"],
              ["","","Bleacher Nation","",""],
              [],
              {
                "google:suggesttype": ["QUERY","QUERY","NAVIGATION","NAVIGATION","QUERY"],
                "google:suggestrelevance": [1250,602,601,700,600]
              }
            ]
        """.trimIndent()
        val parsed = OmniboxEngine.parseLiveSuggestionsResponse(chromePayload)
        assertEquals(1, parsed.navigationalHits.size)
        assertEquals("https://bleachernation.com/", parsed.navigationalHits.first().url)
        assertEquals("bleachernation.com", parsed.navigationalHits.first().displayHost)
        assertEquals("Bleacher Nation", parsed.navigationalHits.first().title)
        assertEquals(
            listOf("bleacher nation", "bleachernation.con", "bleacher nation cubs"),
            parsed.searchQueries
        )

        // Merging promotes nav hit at index 0 and upgrades inline autocomplete when nothing personal matched
        val merged = OmniboxCandidateBuilder.mergeLiveSuggestions(
            existingSuggestions = listOf(
                OmniboxSuggestion(
                    id = "search_self_bleachernation",
                    title = "bleachernation",
                    subtitle = "Search with Google",
                    targetUrl = "https://www.google.com/search?q=bleachernation",
                    kind = SuggestionKind.SEARCH
                )
            ),
            liveResult = parsed,
            settings = BrowserSettings(),
            allowNavPromotion = true
        )
        assertEquals(SuggestionKind.DIRECT_URL, merged.first().kind)
        assertEquals("https://bleachernation.com/", merged.first().targetUrl)

        val navInline = OmniboxEngine.findInlineAutocomplete(
            "bleachernation",
            OmniboxCandidateBuilder.fromNavigationalSuggestions(parsed.navigationalHits)
        )
        assertEquals(".com", navInline?.completionSuffix)
        assertEquals("https://bleachernation.com/", navInline?.targetUrl)

        // 2-character search prefixes without a dot must not be hijacked by remote nav suggestions
        val googleNavPayload = """
            ["go",["https://google.com/","go lang"],[""],[],{"google:suggesttype":["NAVIGATION","QUERY"],"google:suggestrelevance":[1200,900]}]
        """.trimIndent()
        val googleNavParsed = OmniboxEngine.parseLiveSuggestionsResponse(googleNavPayload)
        assertNull(
            OmniboxEngine.findInlineAutocomplete(
                "go",
                OmniboxCandidateBuilder.fromNavigationalSuggestions(googleNavParsed.navigationalHits)
            )
        )

        // Malformed non-primitive elements inside payload arrays are skipped without dropping valid hits
        val malformedPayload = """
            [
              "theverge",
              [{"unexpected":true},"https://theverge.com/","the verge reviews"],
              [[],"The Verge",{"bad":1}],
              [],
              {
                "google:suggesttype": [{"bad":1},"NAVIGATION","QUERY"],
                "google:suggestrelevance": ["bad",900,800]
              }
            ]
        """.trimIndent()
        val malformedParsed = OmniboxEngine.parseLiveSuggestionsResponse(malformedPayload)
        assertEquals(1, malformedParsed.navigationalHits.size)
        assertEquals("https://theverge.com/", malformedParsed.navigationalHits.first().url)
        assertEquals(listOf("the verge reviews"), malformedParsed.searchQueries)

        // Merging must dedupe apex/www root nav hits against OPEN_TAB/BOOKMARK/HISTORY and keep OPEN_TAB above nav hits
        val existingWithOpenTab = listOf(
            OmniboxSuggestion(
                id = "tab_1",
                title = "The Verge",
                subtitle = "Switch to open tab · theverge.com",
                targetUrl = "https://www.theverge.com/",
                kind = SuggestionKind.OPEN_TAB,
                tabId = "1"
            ),
            OmniboxSuggestion(
                id = "search_self_theverge",
                title = "theverge",
                subtitle = "Search with Google",
                targetUrl = "https://www.google.com/search?q=theverge",
                kind = SuggestionKind.SEARCH
            )
        )
        val mergedWithTab = OmniboxCandidateBuilder.mergeLiveSuggestions(
            existingSuggestions = existingWithOpenTab,
            liveResult = OmniboxEngine.parseLiveSuggestionsResponse(
                """
                [
                  "theverge",
                  ["https://theverge.com/","https://theverge.org/"],
                  ["The Verge","The Verge Org"],
                  [],
                  {"google:suggesttype":["NAVIGATION","NAVIGATION"],"google:suggestrelevance":[950,700]}
                ]
                """.trimIndent()
            ),
            settings = BrowserSettings(),
            allowNavPromotion = true
        )
        assertEquals("tab_1", mergedWithTab[0].id)
        assertFalse(mergedWithTab.any { it.id == "nav_https://theverge.com/" })
        assertEquals("nav_https://theverge.org/", mergedWithTab[1].id)

        // Standard OpenSearch (DuckDuckGo/Brave/Kagi) 2-element array still parses cleanly
        val openSearch = OmniboxEngine.parseLiveSuggestionsResponse(
            """["kotlin",["kotlin coroutines","kotlin compose"]]"""
        )
        assertTrue(openSearch.navigationalHits.isEmpty())
        assertEquals(listOf("kotlin coroutines", "kotlin compose"), openSearch.searchQueries)
    }

    @Test
    fun quickTldChipEligibilityAndShortcutApplicationWorkCleanly() {
        assertTrue(OmniboxEngine.shouldShowQuickTldChips("theverge"))
        assertTrue(OmniboxEngine.shouldShowQuickTldChips("theverge."))
        assertTrue(OmniboxEngine.shouldShowQuickTldChips("docs.google."))
        assertTrue(OmniboxEngine.shouldShowQuickTldChips("https://myblog"))
        assertTrue(OmniboxEngine.shouldShowQuickTldChips("9to5google"))

        assertFalse(OmniboxEngine.shouldShowQuickTldChips(""))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("the verge"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("!g"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("@yt"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("theverge.com"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("https://www.theverge.com/tech"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("192.168.86.200"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("2026"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("www."))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("about:blank"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("node.js"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("v1.2"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips(".foo"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("-foo"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("foo-"))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("theverge.."))
        assertFalse(OmniboxEngine.shouldShowQuickTldChips("https://.com"))

        assertEquals("theverge.com", OmniboxEngine.applyTldShortcut("theverge", ".com"))
        assertEquals("theverge.com", OmniboxEngine.applyTldShortcut("theverge.", ".com"))
        assertEquals("https://myblog.org", OmniboxEngine.applyTldShortcut("https://myblog", ".org"))
    }

    @Test
    fun hostPrefixExtractionEscapesSqlLikeWildcards() {
        assertEquals("the\\_verge\\%1", OmniboxEngine.extractHostPrefixForDbQuery("https://www.the_verge%1"))
        assertEquals("youtube", OmniboxEngine.extractHostPrefixForDbQuery("m.youtube"))
        assertEquals("theverge", OmniboxEngine.extractHostPrefixForDbQuery("theverge"))
        assertNull(OmniboxEngine.extractHostPrefixForDbQuery("!g theverge"))
        assertNull(OmniboxEngine.extractHostPrefixForDbQuery("the verge"))
    }

    @Test
    fun inMemoryCandidateBuilderIncludesUnloadedAndRemoteTabsAndIsolatesPrivateMode() {
        val baseState = BrowserUiState(
            isPrivateView = false,
            tabs = listOf(
                BrowserTabUi(
                    id = "t1",
                    spaceId = "space_personal",
                    title = "The Verge",
                    url = "about:blank",
                    isUnloaded = true,
                    pinnedUrl = "https://www.theverge.com/"
                )
            ),
            essentials = listOf(
                EssentialEntity(
                    id = "e1",
                    title = "GitHub",
                    url = "https://github.com/",
                    domain = "github.com"
                )
            ),
            syncedRemoteTabs = listOf(
                SyncedRemoteTabEntity(
                    id = "rt1",
                    deviceId = "dev1",
                    deviceName = "Zen Desktop",
                    title = "Ars Technica",
                    url = "https://arstechnica.com/"
                )
            ),
            history = listOf(
                HistoryEntity(url = "https://overbay.app/", title = "Overbay", visitCount = 5)
            )
        )

        val normalCandidates = OmniboxCandidateBuilder.buildInMemoryCandidates(baseState)
        assertTrue(normalCandidates.any { it.url == "https://www.theverge.com/" && it.priority == OmniboxEngine.PRIORITY_UNLOADED_TAB })
        assertTrue(normalCandidates.any { it.url == "https://arstechnica.com/" && it.priority == OmniboxEngine.PRIORITY_REMOTE_TAB })

        val privateCandidates = OmniboxCandidateBuilder.buildInMemoryCandidates(baseState.copy(isPrivateView = true))
        assertTrue(privateCandidates.isEmpty())
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
