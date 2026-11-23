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
        assertTrue(exampleCosmetic.css.contains(".ad-banner"))
        assertTrue(exampleCosmetic.css.contains(".sponsored-slot"))
        assertTrue(exampleCosmetic.css.contains(".promo"))
        assertFalse(exampleCosmetic.css.contains(".only-other"))
        assertTrue(exampleCosmetic.css.contains("{display:none!important}"))
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
}
