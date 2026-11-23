package app.overbay.swiftfox

import app.overbay.swiftfox.features.adblock.AdblockEngine
import app.overbay.swiftfox.features.adblock.FallbackKotlinAdblockMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdblockEngineTest {

    @Test
    fun infersResourceTypeFromHeadersAndUrlExtension() {
        val engine = AdblockEngine.createForTesting("")

        assertEquals(
            "script",
            engine.inferResourceType(
                "https://cdn.example.com/bundle",
                mapOf("sec-fetch-dest" to "script")
            )
        )
        assertEquals(
            "stylesheet",
            engine.inferResourceType(
                "https://cdn.example.com/asset",
                mapOf("Sec-Fetch-Dest" to "style")
            )
        )
        assertEquals(
            "image",
            engine.inferResourceType(
                "https://cdn.example.com/photo",
                mapOf("Accept" to "image/avif,image/webp,*/*")
            )
        )
        assertEquals(
            "script",
            engine.inferResourceType("https://cdn.example.com/app.js?v=123", emptyMap())
        )
        assertEquals(
            "stylesheet",
            engine.inferResourceType("https://cdn.example.com/main.css", emptyMap())
        )
        assertEquals(
            "image",
            engine.inferResourceType("https://cdn.example.com/logo.webp", emptyMap())
        )
        assertEquals(
            "font",
            engine.inferResourceType("https://cdn.example.com/roboto.woff2", emptyMap())
        )
        assertEquals(
            "media",
            engine.inferResourceType("https://cdn.example.com/stream.m3u8", emptyMap())
        )
        assertEquals(
            "xmlhttprequest",
            engine.inferResourceType(
                "https://api.example.com/data",
                mapOf("Sec-Fetch-Dest" to "empty")
            )
        )
        assertEquals(
            "other",
            engine.inferResourceType("https://example.com", emptyMap())
        )
    }

    @Test
    fun fallbackMatcherHandlesDomainBlockAndExceptionRules() {
        val rules = """
            ! Starter test rules
            ||doubleclick.net^
            ||ads.example.com^
            @@||safe.ads.example.com^
            ||tracker.test/pixel.js${'$'}third-party
        """.trimIndent()

        val matcher = FallbackKotlinAdblockMatcher(rules)

        assertTrue(
            matcher.checkNetworkRequest(
                url = "https://ad.doubleclick.net/pixel.js",
                sourceUrl = "https://news.example.org/article",
                requestType = "script"
            )
        )
        assertFalse(
            matcher.checkNetworkRequest(
                url = "https://notdoubleclick.net/app.js",
                sourceUrl = "https://news.example.org/article",
                requestType = "script"
            )
        )
        assertTrue(
            matcher.checkNetworkRequest(
                url = "https://ads.example.com/banner.png",
                sourceUrl = "https://news.example.org/",
                requestType = "image"
            )
        )
        assertFalse(
            matcher.checkNetworkRequest(
                url = "https://safe.ads.example.com/script.js",
                sourceUrl = "https://news.example.org/",
                requestType = "script"
            )
        )
        assertTrue(
            matcher.checkNetworkRequest(
                url = "https://tracker.test/pixel.js?uid=1",
                sourceUrl = "https://news.example.org/",
                requestType = "script"
            )
        )
        assertFalse(
            matcher.checkNetworkRequest(
                url = "https://tracker.test/pixel.js?uid=1",
                sourceUrl = "https://tracker.test/home",
                requestType = "script"
            )
        )
    }

    @Test
    fun fallbackMatcherHandlesGlobalAndDomainCosmeticSelectors() {
        val rules = """
            ##.ad-banner
            ##.sponsored-slot
            example.com##.promo
            other.org##.only-other
        """.trimIndent()

        val matcher = FallbackKotlinAdblockMatcher(rules)

        val exampleCosmetic = matcher.cosmeticResources("https://sub.example.com/page")
        assertFalse(exampleCosmetic.css.contains(".ad-banner"))
        assertFalse(exampleCosmetic.css.contains(".sponsored-slot"))
        assertTrue(exampleCosmetic.css.contains(".promo"))
        assertFalse(exampleCosmetic.css.contains(".only-other"))
        assertTrue(exampleCosmetic.css.contains("{display:none!important}"))

        val genericCss = matcher.hiddenClassIdCss(
            classes = listOf("ad-banner", "sponsored-slot", "safe-card"),
            ids = emptyList(),
            exceptions = exampleCosmetic.exceptions.toSet()
        )
        assertTrue(genericCss.contains(".ad-banner{display:none!important}"))
        assertTrue(genericCss.contains(".sponsored-slot{display:none!important}"))
        assertFalse(genericCss.contains(".safe-card"))
    }

    @Test
    fun exemptsLanLocalhostAndNonHttpSchemes() {
        val engine = AdblockEngine.createForTesting(
            """
            ||localhost^
            ||192.168.86.200^
            ||overbay.home^
            ||doubleclick.net^
            ##.ad-banner
            """.trimIndent()
        )

        assertFalse(
            engine.shouldBlock(
                requestUrl = "http://localhost:3000/ads.js",
                pageUrl = "http://localhost:3000/",
                resourceType = "script",
                globalEnabled = true,
                siteDisabled = false
            )
        )
        assertFalse(
            engine.shouldBlock(
                requestUrl = "http://192.168.86.200:8080/tracker.js",
                pageUrl = "http://192.168.86.200:8080/",
                resourceType = "script",
                globalEnabled = true,
                siteDisabled = false
            )
        )
        assertFalse(
            engine.shouldBlock(
                requestUrl = "https://stats.overbay.home/script.js",
                pageUrl = "https://dash.overbay.home/",
                resourceType = "script",
                globalEnabled = true,
                siteDisabled = false
            )
        )
        assertFalse(
            engine.shouldBlock(
                requestUrl = "data:text/javascript,alert(1)",
                pageUrl = "https://example.com/",
                resourceType = "script",
                globalEnabled = true,
                siteDisabled = false
            )
        )
        assertTrue(
            engine.shouldBlock(
                requestUrl = "https://doubleclick.net/ads.js",
                pageUrl = "https://example.com/",
                resourceType = "script",
                globalEnabled = true,
                siteDisabled = false
            )
        )
        assertFalse(
            engine.shouldBlock(
                requestUrl = "https://doubleclick.net/ads.js",
                pageUrl = "https://example.com/",
                resourceType = "script",
                globalEnabled = true,
                siteDisabled = true
            )
        )
        assertEquals(
            "",
            engine.cosmeticForUrl(
                url = "http://192.168.86.200:8080/",
                globalEnabled = true,
                cosmeticEnabled = true,
                siteDisabled = false
            ).css
        )
    }

    @Test
    fun fallbackMatcherParsesDomainAndTypeModifiersAndMultiPartTlds() {
        val rules = """
            ||cdn.tracker.net^${'$'}script,domain=news.example.com|~safe.news.example.com
            ||ads.co.uk/pixel.js${'$'}third-party
            ||good.example.com^${'$'}badfilter
            ||good.example.com^${'$'}csp=script-src 'none'
            ||good.example.com^${'$'}redirect=noopjs
        """.trimIndent()

        val matcher = FallbackKotlinAdblockMatcher(rules)

        // Domain + script match
        assertTrue(
            matcher.checkNetworkRequest(
                url = "https://cdn.tracker.net/t.js",
                sourceUrl = "https://news.example.com/post",
                requestType = "script"
            )
        )
        // Wrong resource type -> not blocked
        assertFalse(
            matcher.checkNetworkRequest(
                url = "https://cdn.tracker.net/logo.png",
                sourceUrl = "https://news.example.com/post",
                requestType = "image"
            )
        )
        // Negated domain -> not blocked
        assertFalse(
            matcher.checkNetworkRequest(
                url = "https://cdn.tracker.net/t.js",
                sourceUrl = "https://safe.news.example.com/post",
                requestType = "script"
            )
        )
        // Unrelated domain -> not blocked
        assertFalse(
            matcher.checkNetworkRequest(
                url = "https://cdn.tracker.net/t.js",
                sourceUrl = "https://other.org/",
                requestType = "script"
            )
        )
        // Multi-part public suffix: bbc.co.uk vs ads.co.uk MUST be third-party!
        assertTrue(
            matcher.checkNetworkRequest(
                url = "https://ads.co.uk/pixel.js",
                sourceUrl = "https://www.bbc.co.uk/news",
                requestType = "script"
            )
        )
        // Same registrable site under .co.uk is first-party -> allowed
        assertFalse(
            matcher.checkNetworkRequest(
                url = "https://ads.co.uk/pixel.js",
                sourceUrl = "https://sub.ads.co.uk/home",
                requestType = "script"
            )
        )
        // Unsupported modifiers ($badfilter, $csp, $redirect) must not become blanket blocks
        assertFalse(
            matcher.checkNetworkRequest(
                url = "https://good.example.com/app.js",
                sourceUrl = "https://other.org/",
                requestType = "script"
            )
        )
    }

    @Test
    fun fallbackMatcherIsolatesCosmeticRulesSkipsProceduralAndSupportsGenericLookup() {
        val rules = """
            ##.generic-ad
            ###banner-slot
            ##div:has-text(Sponsored)
            ##.promo:style(opacity:0)
            example.com#@#.generic-ad
            example.com##.site-specific
        """.trimIndent()

        val matcher = FallbackKotlinAdblockMatcher(rules)
        val payload = matcher.cosmeticResources("https://example.com/article")

        assertTrue(payload.css.contains(".site-specific{display:none!important}"))
        assertFalse(payload.css.contains(":has-text("))
        assertFalse(payload.css.contains(":style("))
        assertTrue(payload.exceptions.contains(".generic-ad"))

        val genericCss = matcher.hiddenClassIdCss(
            classes = listOf("generic-ad", "regular-card"),
            ids = listOf("banner-slot", "main"),
            exceptions = payload.exceptions.toSet()
        )
        assertTrue(genericCss.contains("#banner-slot{display:none!important}"))
        assertFalse(genericCss.contains(".generic-ad"))
    }

    @Test
    fun fallbackMatcherHandlesWwwNegationUnknownOptionsImportantPathBoundaryAndCssComments() {
        val rules = """
            ||cdn.tracker.net^${'$'}script,domain=news.example.com|~www.safe.news.example.com
            ||unknown-opt.test^${'$'}futuremodifier
            ||forced.test/ad.js${'$'}script,important
            @@||forced.test/ad.js^
            ||suffix.test/ad^
            example.com##.ad/*comment*/
            example.com##.good-hide
        """.trimIndent()

        val matcher = FallbackKotlinAdblockMatcher(rules)

        assertTrue(matcher.checkNetworkRequest("https://cdn.tracker.net/x.js", "https://news.example.com/", "script"))
        assertFalse(matcher.checkNetworkRequest("https://cdn.tracker.net/x.js", "https://safe.news.example.com/", "script"))
        assertFalse(matcher.checkNetworkRequest("https://unknown-opt.test/app.js", "https://example.com/", "script"))
        assertTrue(matcher.checkNetworkRequest("https://forced.test/ad.js", "https://example.com/", "script"))

        assertTrue(matcher.checkNetworkRequest("https://suffix.test/ad?v=1", "https://example.com/", "script"))
        assertTrue(matcher.checkNetworkRequest("https://suffix.test/ad/", "https://example.com/", "script"))
        assertFalse(matcher.checkNetworkRequest("https://suffix.test/admin.js", "https://example.com/", "script"))

        val css = matcher.cosmeticResources("https://example.com/").css
        assertTrue(css.contains(".good-hide{display:none!important}"))
        assertFalse(css.contains("/*comment*/"))
    }
}
