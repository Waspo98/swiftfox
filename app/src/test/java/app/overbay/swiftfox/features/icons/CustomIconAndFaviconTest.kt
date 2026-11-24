package app.overbay.swiftfox.features.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.overbay.swiftfox.ui.components.DragAxis
import app.overbay.swiftfox.ui.components.DragReorderController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CustomIconAndFaviconTest {

    @Test
    fun customIconSpecParsesAndEncodesAllVariants() {
        assertEquals(CustomIconSpec.None, CustomIconSpec.parse(null))
        assertEquals(CustomIconSpec.None, CustomIconSpec.parse("   "))
        assertNull(CustomIconSpec.None.encode())
        assertNull(CustomIconSpec.None.encodeExplicit())

        val mat = CustomIconSpec.parse("home")
        assertEquals(CustomIconSpec.MaterialIcon("home"), mat)
        assertEquals("home", mat.encode())
        assertEquals("icon:home", mat.encodeExplicit())

        val prefixedMat = CustomIconSpec.parse("icon:Rocket")
        assertEquals(CustomIconSpec.MaterialIcon("rocket"), prefixedMat)
        assertTrue(CustomIconSpec.isExplicitCustomIcon("icon:rocket"))
        assertFalse(CustomIconSpec.isExplicitCustomIcon("rocket"))

        val emoji = CustomIconSpec.parse("emoji:🦊")
        assertEquals(CustomIconSpec.Emoji("🦊"), emoji)
        assertEquals("emoji:🦊", emoji.encode())
        assertEquals("emoji:🦊", emoji.encodeExplicit())
        assertTrue(CustomIconSpec.isExplicitCustomIcon("emoji:🦊"))

        val trimmedEmoji = CustomIconSpec.emojiSpec("ABCD")
        assertEquals(CustomIconSpec.Emoji("AB"), trimmedEmoji)

        val img = CustomIconSpec.parse("image:custom_abc123.png")
        assertEquals(CustomIconSpec.LocalImage("custom_abc123.png"), img)
        assertEquals("image:custom_abc123.png", img.encode())
        assertEquals("image:custom_abc123.png", img.encodeExplicit())
        assertTrue(CustomIconSpec.isExplicitCustomIcon("image:custom_abc123.png"))
    }

    @Test
    fun customIconSpecRejectsPathTraversalInLocalImages() {
        assertEquals(CustomIconSpec.None, CustomIconSpec.parse("image:../secret.png"))
        assertEquals(CustomIconSpec.None, CustomIconSpec.parse("image:foo/bar.png"))
        assertEquals(CustomIconSpec.None, CustomIconSpec.parse("image:foo\\bar.png"))
    }

    @Test
    fun htmlIconExtractorPrioritizesTouchAndPngAndSkipsSvg() {
        val html = """
            <html><head>
              <link rel="icon" type="image/svg+xml" href="/logo.svg">
              <link rel="shortcut icon" href="/favicon.ico">
              <link rel="icon" type="image/png" sizes="32x32" href="/icons/icon-32.png">
              <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">
            </head></html>
        """.trimIndent()

        val candidates = FaviconRepository.extractIconCandidatesFromHtml(html, "https://overbay.app/dash")
        assertEquals(
            listOf(
                "https://overbay.app/apple-touch-icon.png",
                "https://overbay.app/icons/icon-32.png",
                "https://overbay.app/favicon.ico"
            ),
            candidates
        )
    }

    @Test
    fun publicFallbackHostGuardProtectsLanAndLocalHosts() {
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("localhost"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("192.168.86.200"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("10.0.0.5"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("nas.local"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("router.lan"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("[::1]"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("::1"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("::ffff:192.168.1.1"))
        assertFalse(FaviconRepository.isPublicFallbackEligibleHost("intranet"))

        assertTrue(FaviconRepository.isPublicFallbackEligibleHost("github.com"))
        assertTrue(FaviconRepository.isPublicFallbackEligibleHost("overbay.app"))
    }

    @Test
    fun faviconScopeDifferentiatesPortsAndSubpathsOnSameHost() {
        // Different ports on same LAN IP must get distinct keys
        val sonarrPort = FaviconRepository.resolveScope("https://192.168.86.200:8989/")!!
        val radarrPort = FaviconRepository.resolveScope("https://192.168.86.200:7878/")!!
        val qbitPort = FaviconRepository.resolveScope("http://192.168.86.200:8080/")!!
        assertEquals("192.168.86.200:8989", sonarrPort.primaryKey)
        assertEquals("192.168.86.200:7878", radarrPort.primaryKey)
        assertEquals("192.168.86.200:8080", qbitPort.primaryKey)
        assertTrue(sonarrPort.isSubpathScoped)

        // Different subpaths on same IP + same port (default 443) must get distinct keys
        val sonarrPath = FaviconRepository.resolveScope("https://192.168.86.200/sonarr")!!
        val sonarrDeep = FaviconRepository.resolveScope("https://192.168.86.200/sonarr/series/123?tab=files")!!
        val radarrPath = FaviconRepository.resolveScope("https://192.168.86.200/radarr/movies")!!
        val qbitPath = FaviconRepository.resolveScope("https://192.168.86.200/qbittorrent/")!!
        assertEquals("192.168.86.200/sonarr", sonarrPath.primaryKey)
        assertEquals("192.168.86.200/sonarr", sonarrDeep.primaryKey)
        assertEquals("192.168.86.200/radarr", radarrPath.primaryKey)
        assertEquals("192.168.86.200/qbittorrent", qbitPath.primaryKey)

        // Reverse proxy on a custom port with subpaths must include BOTH port and first segment
        val proxySonarr = FaviconRepository.resolveScope("https://192.168.86.200:8443/sonarr/calendar")!!
        val proxyRadarr = FaviconRepository.resolveScope("https://192.168.86.200:8443/radarr/")!!
        assertEquals("192.168.86.200:8443/sonarr", proxySonarr.primaryKey)
        assertEquals("192.168.86.200:8443/radarr", proxyRadarr.primaryKey)
        assertEquals("192.168.86.200:8443", proxySonarr.authorityKey)

        // Public domains keep host-level primaryKey (no per-article spam), while retaining optional pathKey & custom port
        val news1 = FaviconRepository.resolveScope("https://www.nytimes.com/2026/11/story-a")!!
        val news2 = FaviconRepository.resolveScope("http://nytimes.com:80/2026/11/story-b")!!
        val customPortPublic = FaviconRepository.resolveScope("https://home.overbay.app:8989/series")!!
        assertEquals("nytimes.com", news1.primaryKey)
        assertEquals("nytimes.com", news2.primaryKey)
        assertEquals("nytimes.com/2026", news1.pathKey)
        assertFalse(news1.isSubpathScoped)
        assertEquals("home.overbay.app:8989", customPortPublic.authorityKey)
        assertEquals("home.overbay.app:8989", customPortPublic.primaryKey)

        // IPv6 with and without explicit port
        val ipv6Port = FaviconRepository.resolveScope("http://[::1]:3000/sonarr/home")!!
        val ipv6Root = FaviconRepository.resolveScope("http://[::1]/")!!
        assertEquals("[::1]:3000/sonarr", ipv6Port.primaryKey)
        assertEquals("::1", ipv6Root.primaryKey)
    }

    @Test
    fun faviconProbeTargetsRespectSubpathAppsWithoutStealingOriginRoot() {
        val subpathScope = FaviconRepository.resolveScope("https://192.168.86.200/radarr/movies")!!
        val subpathTargets = FaviconRepository.buildProbeTargets("https://192.168.86.200/radarr/movies", subpathScope)
        assertEquals("https://192.168.86.200/radarr/", subpathTargets.htmlProbeUrl)
        assertEquals(
            listOf(
                "https://192.168.86.200/radarr/apple-touch-icon.png",
                "https://192.168.86.200/radarr/favicon.ico"
            ),
            subpathTargets.directIconUrls
        )
        assertTrue(subpathTargets.isSubpathProbe)

        // Mixed-case subpath must preserve original casing for HTTP probes while lowercasing the cache key
        val mixedCaseUrl = "https://192.168.86.200/Nextcloud/apps/files"
        val mixedCaseScope = FaviconRepository.resolveScope(mixedCaseUrl)!!
        assertEquals("192.168.86.200/nextcloud", mixedCaseScope.primaryKey)
        assertEquals("Nextcloud", mixedCaseScope.rawFirstSegment)
        val mixedTargets = FaviconRepository.buildProbeTargets(mixedCaseUrl, mixedCaseScope)
        assertEquals("https://192.168.86.200/Nextcloud/", mixedTargets.htmlProbeUrl)
        assertEquals("https://192.168.86.200/Nextcloud", mixedTargets.alternateHtmlProbeUrl)
        assertEquals(
            listOf(
                "https://192.168.86.200/Nextcloud/apple-touch-icon.png",
                "https://192.168.86.200/Nextcloud/favicon.ico"
            ),
            mixedTargets.directIconUrls
        )

        // Bare IPv6 URL with unencoded space must still bracket [::1] in fallback origin
        val ipv6SpaceUrl = "http://[::1]/Sonarr/series?q=bad space"
        val ipv6Scope = FaviconRepository.resolveScope(ipv6SpaceUrl)!!
        val ipv6Targets = FaviconRepository.buildProbeTargets(ipv6SpaceUrl, ipv6Scope)
        assertEquals("http://[::1]", ipv6Targets.origin)
        assertEquals("http://[::1]/Sonarr/", ipv6Targets.htmlProbeUrl)

        val portRootScope = FaviconRepository.resolveScope("https://192.168.86.200:7878/")!!
        val portRootTargets = FaviconRepository.buildProbeTargets("https://192.168.86.200:7878/", portRootScope)
        assertEquals("https://192.168.86.200:7878/", portRootTargets.htmlProbeUrl)
        assertEquals(
            listOf(
                "https://192.168.86.200:7878/apple-touch-icon.png",
                "https://192.168.86.200:7878/favicon.ico"
            ),
            portRootTargets.directIconUrls
        )
        assertFalse(portRootTargets.isSubpathProbe)
    }

    @Test
    fun faviconAliasGuardProtectsRootDashboardsAndAllowsCustomPortRedirects() {
        // Default-port reverse-proxy dashboard pinned at / must NOT be overwritten when clicking /radarr
        val rootDashboard = FaviconRepository.resolveScope("https://192.168.86.200/")!!
        val radarrClick = FaviconRepository.resolveScope("https://192.168.86.200/radarr/movies")!!
        assertFalse(
            FaviconRepository.shouldWriteAliasFavicon(
                scope = radarrClick,
                aliasScope = rootDashboard,
                targetKey = radarrClick.primaryKey,
                aliasAlreadyCached = false
            )
        )

        // Custom-port app pinned at :8989/ that immediately redirected to :8989/series fills root badge once
        val sonarrRoot = FaviconRepository.resolveScope("https://192.168.86.200:8989/")!!
        val sonarrSeries = FaviconRepository.resolveScope("https://192.168.86.200:8989/series")!!
        assertTrue(
            FaviconRepository.shouldWriteAliasFavicon(
                scope = sonarrSeries,
                aliasScope = sonarrRoot,
                targetKey = sonarrSeries.primaryKey,
                aliasAlreadyCached = false
            )
        )
        // Once the custom-port root already has its own cached icon (e.g. :8443/ dashboard), never overwrite it
        assertFalse(
            FaviconRepository.shouldWriteAliasFavicon(
                scope = sonarrSeries,
                aliasScope = sonarrRoot,
                targetKey = sonarrSeries.primaryKey,
                aliasAlreadyCached = true
            )
        )
    }

    @Test
    fun pixelSampleSimilarityDistinguishesDistinctAppIcons() {
        val blueDocs = IntArray(16) { 0xFF4285F4.toInt() }
        val nearBlueDocs = IntArray(16) { 0xFF4488F0.toInt() }
        val greenSheets = IntArray(16) { 0xFF0F9D58.toInt() }

        assertTrue(FaviconRepository.arePixelSamplesSimilar(blueDocs, nearBlueDocs))
        assertFalse(FaviconRepository.arePixelSamplesSimilar(blueDocs, greenSheets))
        assertFalse(FaviconRepository.arePixelSamplesSimilar(IntArray(0), IntArray(0)))
    }

    @Test
    fun faviconFetchGateDoesNotCancelSharedFetchOrPoisonNegativeCacheWhenCallerCancels() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = TestScope(dispatcher)
        val gate = FaviconFetchGate<String>(
            fetchScope = repoScope,
            fetchDispatcher = dispatcher
        )
        val fetchCount = AtomicInteger(0)
        val networkSignal = CompletableDeferred<String?>()
        val stored = mutableListOf<String>()

        val caller1 = launch(dispatcher) {
            gate.getOrFetch(
                host = "example.com",
                fetcher = {
                    fetchCount.incrementAndGet()
                    networkSignal.await()
                },
                onFetched = { value, _ -> stored += value }
            )
        }
        val caller2 = async(dispatcher) {
            gate.getOrFetch(
                host = "example.com",
                fetcher = {
                    fetchCount.incrementAndGet()
                    networkSignal.await()
                },
                onFetched = { value, _ -> stored += value }
            )
        }

        advanceUntilIdle()
        // Cancel the first caller mid-flight (simulating LazyColumn scroll / recomposition)
        caller1.cancel()
        advanceUntilIdle()

        // Complete network fetch: second caller must still receive it and host must NOT be negatively cached
        networkSignal.complete("icon_example")
        repoScope.advanceUntilIdle()
        advanceUntilIdle()

        assertEquals(1, fetchCount.get())
        assertEquals("icon_example", caller2.await())
        assertEquals(listOf("icon_example"), stored)
        assertFalse(gate.isNegativelyCached("example.com"))
    }

    @Test
    fun faviconFetchGateDropsStaleFetchIfCacheClearedMidFlight() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = TestScope(dispatcher)
        val gate = FaviconFetchGate<String>(
            fetchScope = repoScope,
            fetchDispatcher = dispatcher
        )
        val networkSignal = CompletableDeferred<String?>()
        val stored = mutableListOf<String>()

        val caller = async(dispatcher) {
            gate.getOrFetch(
                host = "news.example.com",
                fetcher = { networkSignal.await() },
                onFetched = { value, _ -> stored += value }
            )
        }
        advanceUntilIdle()

        // User clears browsing data while the favicon request is still in flight
        gate.invalidateAll()
        networkSignal.complete("stale_icon")
        repoScope.advanceUntilIdle()
        advanceUntilIdle()

        assertNull(caller.await())
        assertTrue(stored.isEmpty())
        assertFalse(gate.isNegativelyCached("news.example.com"))
    }

    @Test
    fun privateTabIconStoreIsolatesHostsAndClearsCleanly() {
        val store = PrivateTabIconStore<String>()
        assertNull(store.get("tab_p1", "a.com"))

        store.put("tab_p1", "a.com", "icon_a")
        assertEquals("icon_a", store.get("tab_p1", "a.com"))
        // Navigating the same private tab to another host must not leak Site A's icon
        assertNull(store.get("tab_p1", "b.com"))
        assertNull(store.get("tab_p2", "a.com"))

        assertTrue(store.remove("tab_p1"))
        assertNull(store.get("tab_p1", "a.com"))
        assertFalse(store.remove("tab_p1"))

        store.put("tab_p2", "b.com", "icon_b")
        assertTrue(store.clearAll())
        assertNull(store.get("tab_p2", "b.com"))
        assertFalse(store.clearAll())
    }

    @Test
    fun dragReorderControllerSupportsMultiStep2DGridSwapsAndAntiFlickerSync() {
        var nowMs = 1_000L
        var committed: List<String>? = null
        val controller = DragReorderController<String>(
            keyOf = { it },
            axis = DragAxis.BOTH,
            onCommitOrder = { committed = it },
            clockMs = { nowMs }
        )
        // 2x2 grid:
        // [a (0..100, 0..100)]   [b (100..200, 0..100)]
        // [c (0..100, 100..200)] [d (100..200, 100..200)]
        controller.syncFromSource(listOf("a", "b", "c", "d"))
        controller.updateSlotBounds("a", Rect(0f, 0f, 100f, 100f))
        controller.updateSlotBounds("b", Rect(100f, 0f, 200f, 100f))
        controller.updateSlotBounds("c", Rect(0f, 100f, 100f, 200f))
        controller.updateSlotBounds("d", Rect(100f, 100f, 200f, 200f))

        // Container-level hit-test resolves item "a" at (40, 40)
        assertEquals("a", controller.findKeyAt(Offset(40f, 40f)))
        controller.startDrag("a", haptic = null)

        // Step 1: drag right into slot "b"
        nowMs += 60L
        controller.onDragDelta(Offset(100f, 0f), haptic = null)
        assertEquals(listOf("b", "a", "c", "d"), controller.items.toList())
        // Offset compensated by slot shift (100, 0) so tile stays finger-locked at zero residual offset
        assertEquals(Offset.Zero, controller.dragOffset)

        // Step 2: continue dragging down into row 2 ("d") in the same continuous gesture
        nowMs += 60L
        controller.onDragDelta(Offset(0f, 100f), haptic = null)
        assertEquals(listOf("b", "c", "d", "a"), controller.items.toList())
        assertEquals(Offset.Zero, controller.dragOffset)

        controller.endDrag()
        assertEquals(listOf("b", "c", "d", "a"), committed)

        // Transient stale source emission right after endDrag must not flicker back
        nowMs += 20L
        controller.syncFromSource(listOf("a", "b", "c", "d"))
        assertEquals(listOf("b", "c", "d", "a"), controller.items.toList())

        // Once settled source arrives (or timeout passes), sync succeeds cleanly
        controller.syncFromSource(listOf("b", "c", "d", "a"))
        assertEquals(listOf("b", "c", "d", "a"), controller.items.toList())
    }
}
