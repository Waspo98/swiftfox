package app.overbay.swiftfox.features.webapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAppManifestParserTest {

    @Test
    fun parseManifestResolvesRelativeUrlsColorsIconsAndShortcuts() {
        val raw = """
            {
              "name": "Overbay Notes",
              "short_name": "Notes",
              "start_url": "/app/index.html?utm=pwa",
              "scope": "/app/",
              "display": "standalone",
              "theme_color": "#1a2b3c",
              "background_color": "rgb(255, 255, 255)",
              "icons": [
                { "src": "/icons/logo.svg", "sizes": "any", "type": "image/svg+xml" },
                { "src": "/icons/icon-192.png", "sizes": "192x192", "type": "image/png" },
                { "src": "/icons/maskable-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable" }
              ],
              "shortcuts": [
                { "name": "New Note", "url": "/app/new", "description": "Create note" },
                { "name": "Outside", "url": "/other/page" },
                { "name": "Evil", "url": "https://evil.example/steal" }
              ]
            }
        """.trimIndent()

        val parsed = WebAppManifestParser.parseManifest(
            rawJson = raw,
            manifestUrl = "https://notes.example.com/static/manifest.webmanifest",
            pageUrl = "https://notes.example.com/app/",
            fallbackTitle = "Fallback"
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("Overbay Notes", parsed.name)
        assertEquals("Notes", parsed.shortName)
        assertEquals("https://notes.example.com/app/index.html?utm=pwa", parsed.startUrl)
        assertEquals("https://notes.example.com/app/", parsed.scopeUrl)
        assertEquals(WebAppDisplayMode.STANDALONE, parsed.displayMode)
        assertEquals("#1A2B3C", parsed.themeColorHex)
        assertEquals("#FFFFFF", parsed.backgroundColorHex)
        assertEquals("https://notes.example.com/icons/maskable-512.png", parsed.bestIconUrl)
        assertTrue(parsed.bestIconMaskable)
        assertEquals(1, parsed.shortcuts.size)
        assertEquals("New Note", parsed.shortcuts.single().name)
        assertEquals("https://notes.example.com/app/new", parsed.shortcuts.single().url)
    }

    @Test
    fun parseManifestEnforcesSameOriginStartUrlAndValidScope() {
        val raw = """
            {
              "name": "Cross Origin Attempt",
              "start_url": "https://phishing.example.com/login",
              "scope": "https://phishing.example.com/",
              "display": "fullscreen"
            }
        """.trimIndent()

        val parsed = WebAppManifestParser.parseManifest(
            rawJson = raw,
            manifestUrl = "https://app.example.com/sub/manifest.json",
            pageUrl = "https://app.example.com/sub/home.html"
        )!!
        assertEquals("https://app.example.com/sub/home.html", parsed.startUrl)
        assertEquals("https://app.example.com/sub/", parsed.scopeUrl)
        assertEquals(WebAppDisplayMode.FULLSCREEN, parsed.displayMode)
    }

    @Test
    fun parseManifestSupportsDisplayOverrideFallbackChain() {
        val raw = """
            {
              "name": "Override App",
              "display": "browser",
              "display_override": ["window-controls-overlay", "minimal-ui", "standalone"]
            }
        """.trimIndent()
        val parsed = WebAppManifestParser.parseManifest(
            rawJson = raw,
            manifestUrl = "https://example.com/manifest.json",
            pageUrl = "https://example.com/"
        )!!
        assertEquals(WebAppDisplayMode.MINIMAL_UI, parsed.displayMode)
    }

    @Test
    fun scopeCheckingHandlesSubpathsPortsAndExternalDomains() {
        assertTrue(
            WebAppManifestParser.isUrlInWebAppScope(
                targetUrl = "https://nas.local:8443/app/settings?tab=1",
                startUrl = "https://nas.local:8443/app/index.html",
                scopeUrl = "https://nas.local:8443/app/"
            )
        )
        assertFalse(
            WebAppManifestParser.isUrlInWebAppScope(
                targetUrl = "https://nas.local:8443/other/page",
                startUrl = "https://nas.local:8443/app/index.html",
                scopeUrl = "https://nas.local:8443/app/"
            )
        )
        assertFalse(
            WebAppManifestParser.isUrlInWebAppScope(
                targetUrl = "https://nas.local:9000/app/index.html",
                startUrl = "https://nas.local:8443/app/index.html",
                scopeUrl = "https://nas.local:8443/app/"
            )
        )
        assertFalse(
            WebAppManifestParser.isUrlInWebAppScope(
                targetUrl = "https://external.example.org/",
                startUrl = "https://app.example.com/",
                scopeUrl = "https://app.example.com/"
            )
        )
    }

    @Test
    fun oauthAndRedirectHeuristicsAllowSsoWhileRoutingExternalLinks() {
        assertTrue(
            WebAppManifestParser.isLikelyAuthOrOAuthRedirect(
                targetUrl = "https://accounts.google.com/o/oauth2/v2/auth",
                isRedirect = false,
                hasGesture = true
            )
        )
        assertTrue(
            WebAppManifestParser.isLikelyAuthOrOAuthRedirect(
                targetUrl = "https://sso.company.internal/if/flow/default-authentication/",
                isRedirect = false,
                hasGesture = true
            )
        )
        assertTrue(
            WebAppManifestParser.isLikelyAuthOrOAuthRedirect(
                targetUrl = "https://arbitrary.example.com/callback",
                isRedirect = true,
                hasGesture = true
            )
        )
        assertFalse(
            WebAppManifestParser.isLikelyAuthOrOAuthRedirect(
                targetUrl = "https://arstechnica.com/gadgets/2026/11/article/",
                isRedirect = false,
                hasGesture = true
            )
        )
    }

    @Test
    fun cssColorNormalizationAndLuminanceChecksWorkAcrossFormats() {
        assertEquals("#AABBCC", WebAppManifestParser.normalizeCssColorHex("#abc"))
        assertEquals("#112233", WebAppManifestParser.normalizeCssColorHex(" #112233 "))
        assertEquals("#80112233", WebAppManifestParser.normalizeCssColorHex("#11223380"))
        assertEquals("#0FA0FF", WebAppManifestParser.normalizeCssColorHex("rgb(15, 160, 255)"))
        assertNull(WebAppManifestParser.normalizeCssColorHex("transparent"))
        assertNull(WebAppManifestParser.normalizeCssColorHex("rgba(0, 0, 0, 0)"))

        assertTrue(WebAppManifestParser.isLightColor(0xFFFFFFFF.toInt()))
        assertFalse(WebAppManifestParser.isLightColor(0xFF121212.toInt()))
    }

    @Test
    fun buildInstallCandidateDetectsMetaCapableWithoutManifest() {
        val meta = PageWebAppMetadata(
            pageUrl = "https://192.168.1.50:8123/lovelace/0",
            pageTitle = "Home Assistant",
            appleMobileCapable = true,
            themeColorRaw = "#03a9f4",
            touchIcons = listOf(
                ManifestIconCandidate("https://192.168.1.50:8123/static/icons/favicon-192x192.png", sizes = "192x192")
            )
        )
        val candidate = WebAppManifestParser.buildInstallCandidate(meta, manifest = null)
        assertTrue(candidate.isStandaloneCapable)
        assertEquals(WebAppDisplayMode.STANDALONE, candidate.displayMode)
        assertEquals("Home Assistant", candidate.suggestedName)
        assertEquals("#03A9F4", candidate.themeColorHex)
        assertEquals("https://192.168.1.50:8123/static/icons/favicon-192x192.png", candidate.bestIconUrl)
    }

    @Test
    fun parseThemeSurfaceMessageExtractsMetaSampledEdgesAndViewportFit() {
        val raw = """
            {
              "channel": "pwa",
              "event": "themeSurface",
              "themeColorRaw": "#0d1117",
              "sampledTopColorRaw": "rgb(22, 27, 34)",
              "sampledBottomColorRaw": "rgba(255, 255, 255, 1)",
              "viewportFitCover": true
            }
        """.trimIndent()
        val parsed = WebAppDetector.parseThemeSurfaceMessage(raw)!!
        assertEquals("#0D1117", parsed.metaThemeColorHex)
        assertEquals("#161B22", parsed.sampledTopColorHex)
        assertEquals("#FFFFFF", parsed.sampledBottomColorHex)
        assertTrue(parsed.viewportFitCover)

        val empty = WebAppDetector.parseThemeSurfaceMessage("""{"themeColorRaw":"transparent","viewportFitCover":false}""")!!
        assertNull(empty.metaThemeColorHex)
        assertNull(empty.sampledTopColorHex)
        assertNull(empty.sampledBottomColorHex)
        assertFalse(empty.viewportFitCover)
    }

    @Test
    fun sanitizeMobileUserAgentStripsWebViewMarkersAndEnsuresMobileToken() {
        val phoneUa = "Mozilla/5.0 (Linux; Android 15; Pixel 9; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/136.0.0.0 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36",
            app.overbay.swiftfox.engine.BrowserSession.sanitizeMobileUserAgent(phoneUa)
        )

        val tabletLikeUa = "Mozilla/5.0 (Linux; Android 15; Pixel Fold; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/136.0.0.0 Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 15; Pixel Fold) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36",
            app.overbay.swiftfox.engine.BrowserSession.sanitizeMobileUserAgent(tabletLikeUa)
        )

        val viewportScript = app.overbay.swiftfox.engine.ScriptInjector.buildEnsureMobileViewportScript()
        assertTrue(viewportScript.contains("width=device-width"))
        assertTrue(viewportScript.contains("initial-scale=1"))
    }
}
