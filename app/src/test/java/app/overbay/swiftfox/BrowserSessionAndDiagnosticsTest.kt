package app.overbay.swiftfox

import app.overbay.swiftfox.engine.BrowserSession
import app.overbay.swiftfox.engine.NetworkErrorDiagnostics
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
}
