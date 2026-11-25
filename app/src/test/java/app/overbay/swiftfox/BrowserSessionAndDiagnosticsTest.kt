package app.overbay.swiftfox

import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.engine.BridgeTokenSet
import app.overbay.swiftfox.engine.BrowserSession
import app.overbay.swiftfox.engine.NetworkErrorDiagnostics
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

class BrowserSessionAndDiagnosticsTest {

    @Test
    fun sanitizeMobileUserAgentStripsWebViewMarkers() {
        val raw = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro Fold Build/AP3A.241005.015; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/136.0.7103.60 Mobile Safari/537.36"
        val cleaned = BrowserSession.sanitizeMobileUserAgent(raw)
        assertFalse(cleaned.contains("; wv"))
        assertFalse(cleaned.contains("Version/4.0"))
        assertEquals(
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro Fold Build/AP3A.241005.015) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.7103.60 Mobile Safari/537.36",
            cleaned
        )
    }

    @Test
    fun sanitizeMobileUserAgentLeavesCleanDesktopUaUntouched() {
        val desktop = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        assertEquals(desktop, BrowserSession.sanitizeMobileUserAgent(desktop))
    }

    @Test
    fun networkDiagnosticsExtractsRawHostAndOriginForUrlsWithUnencodedQueries() {
        val messyUrl = "https://www.overbay.app/search?q=cubs|bears {2026} [live]&token=secret"
        assertEquals("www.overbay.app", NetworkErrorDiagnostics.extractRawHost(messyUrl))
        assertEquals("https://www.overbay.app/", NetworkErrorDiagnostics.buildOriginProbeUrl(messyUrl))

        val customPort = "https://service.overbay.app:8443/api/v1?x=a b"
        assertEquals("service.overbay.app", NetworkErrorDiagnostics.extractRawHost(customPort))
        assertEquals("https://service.overbay.app:8443/", NetworkErrorDiagnostics.buildOriginProbeUrl(customPort))

        val ipv6 = "http://[::1]:8080/status?filter=a|b"
        assertEquals("::1", NetworkErrorDiagnostics.extractRawHost(ipv6))
        assertEquals("http://[::1]:8080/", NetworkErrorDiagnostics.buildOriginProbeUrl(ipv6))
    }

    @Test
    fun networkDiagnosticsSkipsDirectHttpProbeWhenDisabledForPrivateTabs() = runTest {
        val probeCalls = AtomicInteger(0)
        val instrumentedClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                probeCalls.incrementAndGet()
                chain.proceed(chain.request())
            }
            .build()

        val result = NetworkErrorDiagnostics.diagnose(
            url = "https://127.0.0.1/private?secret=1",
            description = "net::ERR_CONNECTION_REFUSED",
            baseClient = instrumentedClient,
            runDirectProbe = false
        )
        assertEquals(0, probeCalls.get())
        assertNull(result.directProbeSummary)
        assertTrue(result.hasSinkholeOrLoopbackIp)
    }

    @Test
    fun networkDiagnosticsFlagsSinkholeLoopbackAndUnexpectedLanIpsForPublicHosts() {
        assertTrue(
            NetworkErrorDiagnostics.isSinkholeOrUnexpectedLocalIp(
                "overbay.app",
                InetAddress.getByName("0.0.0.0")
            )
        )
        assertTrue(
            NetworkErrorDiagnostics.isSinkholeOrUnexpectedLocalIp(
                "overbay.app",
                InetAddress.getByName("127.0.0.1")
            )
        )
        assertTrue(
            NetworkErrorDiagnostics.isSinkholeOrUnexpectedLocalIp(
                "overbay.app",
                InetAddress.getByName("::1")
            )
        )
        assertTrue(
            NetworkErrorDiagnostics.isSinkholeOrUnexpectedLocalIp(
                "overbay.app",
                InetAddress.getByName("192.168.86.200")
            )
        )
        assertFalse(
            NetworkErrorDiagnostics.isSinkholeOrUnexpectedLocalIp(
                "overbay.app",
                InetAddress.getByName("104.21.80.141")
            )
        )
        // Genuine LAN hosts should not be flagged as unexpected LAN redirects
        assertFalse(
            NetworkErrorDiagnostics.isSinkholeOrUnexpectedLocalIp(
                "192.168.86.200",
                InetAddress.getByName("192.168.86.200")
            )
        )
    }

    @Test
    fun networkDiagnosticsFormatsClearMessages() {
        val sinkholeMsg = NetworkErrorDiagnostics.formatDiagnosticMessage(
            host = "overbay.app",
            description = "net::ERR_CONNECTION_REFUSED",
            resolvedIps = listOf("0.0.0.0"),
            hasSinkholeOrLoopbackIp = true,
            directProbeSummary = null
        )
        assertTrue(sinkholeMsg.contains("0.0.0.0"))
        assertTrue(sinkholeMsg.contains("Private DNS"))

        val publicProbeMsg = NetworkErrorDiagnostics.formatDiagnosticMessage(
            host = "overbay.app",
            description = "net::ERR_CONNECTION_REFUSED",
            resolvedIps = listOf("104.21.80.141", "172.67.185.14"),
            hasSinkholeOrLoopbackIp = false,
            directProbeSummary = "HTTP 200 (reachable outside WebView)"
        )
        assertTrue(publicProbeMsg.contains("104.21.80.141"))
        assertTrue(publicProbeMsg.contains("HTTP 200"))
    }

    @Test
    fun generateHexTokenAndBridgeTokenSetProduceUnique32CharHexTokens() {
        val generated = (1..64).map { BrowserSession.generateHexToken() }
        assertEquals(64, generated.toSet().size)
        for (token in generated) {
            assertEquals(32, token.length)
            assertTrue(token.all { it in '0'..'9' || it in 'a'..'f' })
        }

        val set = BridgeTokenSet.generate()
        assertEquals(
            6,
            setOf(
                set.mediaToken,
                set.zapperToken,
                set.darkReaderToken,
                set.cosmeticToken,
                set.gmToken,
                set.readerToken
            ).size
        )
        assertTrue(set.cosmeticKey.startsWith("__sfCosm_"))
    }

    @Test
    fun selectTokensForOriginRetainsBfCacheTokensAcrossOriginsAndEvictsOldest() {
        val cache = LinkedHashMap<String, BridgeTokenSet>(8, 0.75f, true)
        val originA = BrowserSession.selectTokensForOrigin("https://a.example", cache, maxEntries = 3)
        val originB = BrowserSession.selectTokensForOrigin("https://b.example", cache, maxEntries = 3)
        assertNotEquals(originA.gmToken, originB.gmToken)

        // Navigating back to origin A (e.g. WebView BFCache restore) reuses its exact token set
        val originABack = BrowserSession.selectTokensForOrigin("https://a.example", cache, maxEntries = 3)
        assertSame(originA, originABack)

        // Touching A made B the eldest entry; adding C and D evicts B while retaining A
        BrowserSession.selectTokensForOrigin("https://c.example", cache, maxEntries = 3)
        BrowserSession.selectTokensForOrigin("https://d.example", cache, maxEntries = 3)
        assertEquals(3, cache.size)
        assertSame(originA, cache["https://a.example"])
        assertNull(cache["https://b.example"])
    }

    @Test
    fun postureOverrideTransitionTracksCommittedSnapshotAcrossBackgroundChanges() {
        val mobile = BrowserSession.SiteOverrideSnapshot(
            effectiveMode = SiteMode.MOBILE,
            customUa = null,
            jsEnabled = true,
            cookiesEnabled = true
        )
        val desktop = mobile.copy(effectiveMode = SiteMode.DESKTOP)

        // Tab loaded in MOBILE goes to background while device unfolds to DESKTOP (reloadIfChanged = false)
        val (bgReload, committedAfterUnfold) = BrowserSession.shouldReloadForOverrideTransition(
            committedSnapshot = mobile,
            previousAppliedSnapshot = mobile,
            nextSnapshot = desktop,
            reloadIfChanged = false,
            currentUrl = "https://arstechnica.com/gadgets/"
        )
        assertFalse(bgReload)
        assertEquals(mobile, committedAfterUnfold)

        // Case A: User switches to that background tab while still unfolded -> reloads once into DESKTOP
        val (fgReloadOnSelect, committedAfterSelect) = BrowserSession.shouldReloadForOverrideTransition(
            committedSnapshot = committedAfterUnfold,
            previousAppliedSnapshot = desktop,
            nextSnapshot = desktop,
            reloadIfChanged = true,
            currentUrl = "https://arstechnica.com/gadgets/"
        )
        assertTrue(fgReloadOnSelect)
        assertEquals(desktop, committedAfterSelect)

        // Case B: User refolds back to MOBILE before ever viewing the tab -> zero reload needed
        val (fgReloadAfterRefold, committedAfterRefold) = BrowserSession.shouldReloadForOverrideTransition(
            committedSnapshot = committedAfterUnfold,
            previousAppliedSnapshot = desktop,
            nextSnapshot = mobile,
            reloadIfChanged = true,
            currentUrl = "https://arstechnica.com/gadgets/"
        )
        assertFalse(fgReloadAfterRefold)
        assertEquals(mobile, committedAfterRefold)

        // Start page / non-HTTP URLs never trigger smart reload
        val (blankReload, _) = BrowserSession.shouldReloadForOverrideTransition(
            committedSnapshot = mobile,
            previousAppliedSnapshot = mobile,
            nextSnapshot = desktop,
            reloadIfChanged = true,
            currentUrl = "about:blank"
        )
        assertFalse(blankReload)
    }

    @Test
    fun webViewClientResolveCachedPageHostRemainsAtomicUnderConcurrentAccess() {
        val client = app.overbay.swiftfox.engine.SwiftfoxWebViewClient(
            tabId = "tab-race",
            callbacks = app.overbay.swiftfox.engine.NoOpSessionHostCallbacks
        )
        val urlsAndExpected = listOf(
            "https://www.github.com/Waspo98/swiftfox" to "github.com",
            "https://news.ycombinator.com/item?id=1" to "news.ycombinator.com",
            "https://docs.overbay.app/guide" to "docs.overbay.app",
            "http://192.168.1.20:8080/status" to "192.168.1.20"
        )
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val start = java.util.concurrent.CountDownLatch(1)
        val threads = (0 until 6).map { tid ->
            Thread {
                try {
                    start.await()
                    repeat(200) { i ->
                        val (url, expected) = urlsAndExpected[(tid + i) % urlsAndExpected.size]
                        assertEquals(expected, client.resolveCachedPageHost(url))
                    }
                } catch (t: Throwable) {
                    errors += t
                }
            }
        }
        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join(10_000) }
        assertTrue("Concurrent resolveCachedPageHost errors: $errors", errors.isEmpty())
    }
}
