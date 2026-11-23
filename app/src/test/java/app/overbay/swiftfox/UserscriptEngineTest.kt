package app.overbay.swiftfox

import app.overbay.swiftfox.data.db.UserscriptDao
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.UserscriptStorageDao
import app.overbay.swiftfox.data.db.UserscriptStorageEntity
import app.overbay.swiftfox.features.userscripts.UserscriptBridge
import app.overbay.swiftfox.features.userscripts.UserscriptParser
import app.overbay.swiftfox.features.userscripts.UserscriptRepository
import app.overbay.swiftfox.features.userscripts.UserscriptRunAt
import app.overbay.swiftfox.features.userscripts.UserscriptUrlMatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptEngineTest {

    @Test
    fun parsesAllHeadersWithCrlfAndWhitespace() {
        val raw = "\uFEFF" +
            "  //   ==UserScript==  \r\n" +
            "  //   @name         Dark Fox Tweaks  \r\n" +
            "  //   @namespace    https://overbay.app/scripts \r\n" +
            "  //   @version      2.4.1 \r\n" +
            "  //   @description  Enhances reader readability \r\n" +
            "  //   @match        https://*.example.com/* \r\n" +
            "  //   @include      http*://docs.swiftfox.test/* \r\n" +
            "  //   @exclude      https://admin.example.com/* \r\n" +
            "  //   @run-at       document-idle \r\n" +
            "  //   @grant        GM_addStyle \r\n" +
            "  //   @grant        GM_xmlhttpRequest \r\n" +
            "  //   ==/UserScript== \r\n" +
            "console.log('ready');\r\n"

        val parsed = UserscriptParser.parse(raw, fallbackName = "Fallback")
        assertEquals("Dark Fox Tweaks", parsed.name)
        assertEquals("https://overbay.app/scripts", parsed.namespace)
        assertEquals("2.4.1", parsed.version)
        assertEquals("Enhances reader readability", parsed.description)
        assertEquals(
            listOf("https://*.example.com/*", "http*://docs.swiftfox.test/*"),
            parsed.matches
        )
        assertEquals(listOf("https://admin.example.com/*"), parsed.excludes)
        assertEquals(UserscriptRunAt.DOCUMENT_IDLE, parsed.runAt)
        assertEquals(listOf("GM_addStyle", "GM_xmlhttpRequest"), parsed.grants)
    }

    @Test
    fun parserUsesDefaultsWhenHeaderMissing() {
        val parsed = UserscriptParser.parse("console.log('bare');", fallbackName = "My Custom Script")
        assertEquals("My Custom Script", parsed.name)
        assertEquals("", parsed.namespace)
        assertEquals("1.0", parsed.version)
        assertEquals("", parsed.description)
        assertTrue(parsed.matches.isEmpty())
        assertTrue(parsed.excludes.isEmpty())
        assertEquals(UserscriptRunAt.DOCUMENT_END, parsed.runAt)
        assertTrue(parsed.grants.isEmpty())

        assertEquals(UserscriptRunAt.DOCUMENT_START, UserscriptRunAt.fromHeader("DOCUMENT-START"))
        assertEquals(UserscriptRunAt.DOCUMENT_END, UserscriptRunAt.fromHeader(null))
        assertEquals(UserscriptRunAt.DOCUMENT_END, UserscriptRunAt.fromHeader("unknown"))
    }

    @Test
    fun jsonListHelpersRoundTripAndHandleMalformed() {
        val original = listOf("https://example.com/*", "GM_xmlhttpRequest")
        val encoded = UserscriptParser.encodeStringList(original)
        assertEquals(original, UserscriptParser.decodeStringList(encoded))
        assertTrue(UserscriptParser.decodeStringList("").isEmpty())
        assertTrue(UserscriptParser.decodeStringList("not-valid-json").isEmpty())
    }

    @Test
    fun urlMatcherSupportsAllUrlsSubdomainsExcludesRegexAndSkipsNonHttp() {
        assertTrue(UserscriptUrlMatcher.matches("https://example.com/path", listOf("<all_urls>"), emptyList()))
        assertTrue(UserscriptUrlMatcher.matches("http://192.168.86.200:8080/ui", listOf("<all_urls>"), emptyList()))

        val subdomainMatch = listOf("https://*.example.com/*")
        assertTrue(UserscriptUrlMatcher.matches("https://example.com/", subdomainMatch, emptyList()))
        assertTrue(UserscriptUrlMatcher.matches("https://example.com", subdomainMatch, emptyList()))
        assertTrue(UserscriptUrlMatcher.matches("https://sub.example.com/a/b?x=1", subdomainMatch, emptyList()))
        assertTrue(UserscriptUrlMatcher.matches("https://a.b.example.com/c", subdomainMatch, emptyList()))
        assertFalse(UserscriptUrlMatcher.matches("https://notexample.com/", subdomainMatch, emptyList()))
        assertFalse(UserscriptUrlMatcher.matches("http://example.com/", subdomainMatch, emptyList()))

        val wildcardScheme = listOf("*://example.com/watch*")
        assertTrue(UserscriptUrlMatcher.matches("http://example.com/watch?v=1", wildcardScheme, emptyList()))
        assertTrue(UserscriptUrlMatcher.matches("https://example.com/watch", wildcardScheme, emptyList()))

        assertFalse(
            UserscriptUrlMatcher.matches(
                url = "https://admin.example.com/settings",
                matches = subdomainMatch,
                excludes = listOf("https://admin.example.com/*")
            )
        )

        val regexMatch = listOf("""/^https:\/\/docs\.example\.org\/v\d+\//i""")
        assertTrue(UserscriptUrlMatcher.matches("https://DOCS.example.org/v2/guide", regexMatch, emptyList()))
        assertFalse(UserscriptUrlMatcher.matches("https://docs.example.org/latest/guide", regexMatch, emptyList()))

        val globInclude = listOf("http*://*.google.*/*")
        assertTrue(UserscriptUrlMatcher.matches("https://www.google.co.uk/search?q=fox", globInclude, emptyList()))
        assertFalse(UserscriptUrlMatcher.matches("https://evil.com/www.google.com/", globInclude, emptyList()))

        assertFalse(UserscriptUrlMatcher.matches("https://example.com/", emptyList(), emptyList()))
        assertFalse(UserscriptUrlMatcher.matches("about:blank", listOf("<all_urls>"), emptyList()))
        assertFalse(UserscriptUrlMatcher.matches("file:///sdcard/test.html", listOf("<all_urls>"), emptyList()))
        assertFalse(UserscriptUrlMatcher.matches("javascript:alert(1)", listOf("<all_urls>"), emptyList()))

        // Nested quantifiers & oversized user regex patterns must fail closed (ReDoS protection)
        assertFalse(UserscriptUrlMatcher.matches("https://aaaaaaaaaaaaaa.example.com/", listOf("/(a+)+$/"), emptyList()))
        val overSizedRegex = "/" + "a".repeat(260) + "/"
        assertFalse(UserscriptUrlMatcher.matches("https://example.com/" + "a".repeat(260), listOf(overSizedRegex), emptyList()))
    }

    @Test
    fun bridgeBuildShimAndPerScriptSnippetsEnforceIsolationAndUrlGuards() {
        val scriptDao = FakeUserscriptDao()
        val storageDao = FakeUserscriptStorageDao()
        val bridge = UserscriptBridge(
            context = null,
            scope = kotlinx.coroutines.CoroutineScope(StandardTestDispatcher()),
            scriptDao = scriptDao,
            storageDao = storageDao
        )

        assertEquals("", bridge.buildShimInitScript(bridgeToken = "tok-1", runtimeKey = "rk1"))
        assertTrue(bridge.buildPerScriptSnippets(emptyList(), UserscriptRunAt.DOCUMENT_END).isEmpty())

        val entity = UserscriptEntity(
            id = "script-1",
            name = "Test Script",
            namespace = "swiftfox",
            version = "1.2",
            description = "Demo",
            code = "GM_addStyle('body { color: red; }');\n// trailing comment without newline",
            matchesJson = UserscriptParser.encodeStringList(listOf("https://example.com/*")),
            excludesJson = UserscriptParser.encodeStringList(listOf("https://example.com/private/*")),
            runAt = UserscriptRunAt.DOCUMENT_IDLE.headerValue,
            grantsJson = UserscriptParser.encodeStringList(listOf("GM_addStyle", "GM_getValue"))
        )
        val storage = mapOf(
            "count" to "42",
            "theme" to "\"amber\""
        )

        val endSnippets = bridge.buildPerScriptSnippets(
            scriptsWithStorage = listOf(entity to storage),
            runAt = UserscriptRunAt.DOCUMENT_END,
            bridgeToken = "tok-1",
            runtimeKey = "rk1",
            includeUrlGuard = true
        )
        assertEquals(1, endSnippets.size)
        val endJs = endSnippets.single()
        assertTrue(endJs.contains("window[\"rk1\"]"))
        assertTrue(endJs.contains("__sfRt.createApi(\"tok-1\", \"script-1\""))
        assertTrue(endJs.contains("\"count\":42"))
        assertTrue(endJs.contains("\"theme\":\"amber\""))
        assertTrue(endJs.contains("(function(GM_info, GM_addStyle, GM_getValue, GM_setValue, GM_deleteValue, GM_listValues, GM_xmlhttpRequest, GM, unsafeWindow) {"))
        assertTrue(endJs.contains("}).call(window, __sfApi.GM_info"))
        assertTrue(endJs.contains("__sfHref"))
        assertTrue(endJs.contains("__swiftfoxRanUserscripts"))
        assertTrue(endJs.contains("// trailing comment without newline\n    }).call(window"))
        assertFalse(endJs.contains("setTimeout(function()"))

        val idleJs = bridge.buildPerScriptSnippets(
            scriptsWithStorage = listOf(entity to storage),
            runAt = UserscriptRunAt.DOCUMENT_IDLE,
            runtimeKey = "rk1"
        ).single()
        assertTrue(idleJs.contains("setTimeout(function() {"))
        assertTrue(idleJs.contains("}, 0);"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun bridgeHandlesStorageAndEnforcesXhrGrantsConnectAndScheme() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scriptDao = FakeUserscriptDao()
        val storageDao = FakeUserscriptStorageDao()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .addHeader("X-Test", "Swiftfox")
                    .body("{\"ok\":true}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val bridge = UserscriptBridge(
            context = null,
            scope = this,
            scriptDao = scriptDao,
            storageDao = storageDao,
            client = mockClient,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher
        )

        // Unknown script cannot write to storage
        bridge.handleSetValue("unknown-script", "counter", "7")
        advanceUntilIdle()
        assertEquals(null, storageDao.getValue("unknown-script", "counter"))

        // Enabled granted script can write and delete storage on matching page
        scriptDao.upsert(
            UserscriptEntity(
                id = "s1",
                name = "Storage Script",
                code = "console.log(0);",
                matchesJson = UserscriptParser.encodeStringList(listOf("https://example.com/*")),
                grantsJson = UserscriptParser.encodeStringList(listOf("GM_setValue", "GM_deleteValue")),
                enabled = true
            )
        )
        bridge.handleSetValue("s1", "counter", "7", pageUrl = "https://example.com/page")
        advanceUntilIdle()
        assertEquals("7", storageDao.getValue("s1", "counter")?.valueJson)

        // Fail-closed: blank or missing pageUrl must be rejected for storage mutations
        bridge.handleSetValue("s1", "counter", "888", pageUrl = null)
        bridge.handleSetValue("s1", "counter", "888", pageUrl = "   ")
        advanceUntilIdle()
        assertEquals("7", storageDao.getValue("s1", "counter")?.valueJson)

        // Non-matching page cannot mutate script storage
        bridge.handleSetValue("s1", "counter", "999", pageUrl = "https://evil.org/attack")
        advanceUntilIdle()
        assertEquals("7", storageDao.getValue("s1", "counter")?.valueJson)

        bridge.handleDeleteValue("s1", "counter", pageUrl = "https://example.com/page")
        advanceUntilIdle()
        assertEquals(null, storageDao.getValue("s1", "counter"))

        // Script with @grant none (even alongside GM_xmlhttpRequest) must be denied XHR and storage
        scriptDao.upsert(
            UserscriptEntity(
                id = "no-xhr",
                name = "No XHR",
                code = "console.log(1);",
                matchesJson = UserscriptParser.encodeStringList(listOf("<all_urls>")),
                grantsJson = UserscriptParser.encodeStringList(listOf("none", "GM_xmlhttpRequest")),
                enabled = true
            )
        )
        bridge.handleSetValue("no-xhr", "k", "1", pageUrl = "https://example.com/")
        advanceUntilIdle()
        assertEquals(null, storageDao.getValue("no-xhr", "k"))

        var deniedJs: String? = null
        bridge.handleXmlHttpRequest(
            scriptId = "no-xhr",
            reqId = "req-1",
            requestJson = """{"method":"GET","url":"https://example.com/api"}""",
            pageUrl = "https://example.com/",
            bridgeToken = "tok"
        ) { deniedJs = it }
        advanceUntilIdle()
        assertNotNull(deniedJs)
        assertTrue(deniedJs!!.contains("r.resolveXhr(\"tok\",\"req-1\",\"onerror\""))

        // Script with GM.xmlHttpRequest must fail closed on missing pageUrl and reject file:// scheme / SSRF
        scriptDao.upsert(
            UserscriptEntity(
                id = "with-xhr",
                name = "With XHR",
                code = "console.log(2);",
                matchesJson = UserscriptParser.encodeStringList(listOf("<all_urls>")),
                grantsJson = UserscriptParser.encodeStringList(listOf("GM.xmlHttpRequest")),
                enabled = true
            )
        )
        var missingPageJs: String? = null
        bridge.handleXmlHttpRequest(
            scriptId = "with-xhr",
            reqId = "req-nopage",
            requestJson = """{"method":"GET","url":"https://api.example.com/v1"}""",
            pageUrl = null,
            bridgeToken = "tok"
        ) { missingPageJs = it }
        advanceUntilIdle()
        assertNotNull(missingPageJs)
        assertTrue(missingPageJs!!.contains("\"onerror\""))

        var badSchemeJs: String? = null
        bridge.handleXmlHttpRequest(
            scriptId = "with-xhr",
            reqId = "req-2",
            requestJson = """{"method":"GET","url":"file:///etc/passwd"}""",
            pageUrl = "https://example.com/",
            bridgeToken = "tok"
        ) { badSchemeJs = it }
        advanceUntilIdle()
        assertNotNull(badSchemeJs)
        assertTrue(badSchemeJs!!.contains("\"onerror\""))

        var ssrfJs: String? = null
        bridge.handleXmlHttpRequest(
            scriptId = "with-xhr",
            reqId = "req-ssrf",
            requestJson = """{"method":"GET","url":"http://192.168.86.200:8080/admin"}""",
            pageUrl = "https://public.example.com/",
            bridgeToken = "tok"
        ) { ssrfJs = it }
        advanceUntilIdle()
        assertNotNull(ssrfJs)
        assertTrue(ssrfJs!!.contains("\"onerror\""))

        // Granted script with https:// URL succeeds via OkHttp (including GET with stray data field)
        var getWithDataJs: String? = null
        bridge.handleXmlHttpRequest(
            scriptId = "with-xhr",
            reqId = "req-get",
            requestJson = """{"method":"GET","url":"https://api.example.com/v1","data":"ignored"}""",
            pageUrl = "https://public.example.com/",
            bridgeToken = "tok"
        ) { getWithDataJs = it }
        advanceUntilIdle()
        assertNotNull(getWithDataJs)
        assertTrue(getWithDataJs!!.contains("\"onload\""))

        var okJs: String? = null
        bridge.handleXmlHttpRequest(
            scriptId = "with-xhr",
            reqId = "req-3",
            requestJson = """{"method":"POST","url":"https://api.example.com/v1","data":"ping","timeout":500}""",
            pageUrl = "https://public.example.com/",
            bridgeToken = "tok"
        ) { okJs = it }
        advanceUntilIdle()
        assertNotNull(okJs)
        val resolvedOk = okJs!!
        assertTrue(resolvedOk.contains("\"onload\""))
        assertTrue(resolvedOk.contains("200"))
        assertTrue(resolvedOk.contains("X-Test: Swiftfox"))
    }

    @Test
    fun parserAndUrlMatcherHandleExcludeMatchConnectFragmentsPortsAndUnsupportedDirectives() {
        val meta = UserscriptParser.parse(
            """
                // ==UserScript==
                // @name          Connect Demo
                // @match         https://example.com:443/article
                // @include       https://blog.example.org
                // @exclude-match https://example.com/private/*
                // @connect       api.example.com
                // @require       https://cdn.example.com/lib.js
                // ==/UserScript==
                console.log('ok');
            """.trimIndent()
        )
        assertEquals(listOf("https://example.com/private/*"), meta.excludes)
        assertEquals(listOf("api.example.com"), meta.connects)
        assertEquals(listOf("@require"), meta.unsupportedDirectives)

        // Default port normalization + fragment stripping
        assertTrue(
            UserscriptUrlMatcher.matches(
                url = "https://example.com/article#section-2",
                matches = meta.matches,
                excludes = meta.excludes
            )
        )
        // Trailing-slash-less @include matches root URL with trailing slash
        assertTrue(
            UserscriptUrlMatcher.matches(
                url = "https://blog.example.org/",
                matches = meta.matches,
                excludes = meta.excludes
            )
        )
        assertFalse(
            UserscriptUrlMatcher.matches(
                url = "https://example.com/private/secret",
                matches = listOf("https://example.com/*"),
                excludes = meta.excludes
            )
        )
    }

    @Test
    fun repositoryLifecycleMatchesAndCleansStorageOnDelete() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scriptDao = FakeUserscriptDao()
        val storageDao = FakeUserscriptStorageDao()
        val repo = UserscriptRepository(
            scriptDao = scriptDao,
            storageDao = storageDao,
            client = OkHttpClient(),
            ioDispatcher = dispatcher
        )

        val installed = repo.installFromCode(
            code = """
                // ==UserScript==
                // @name Auto Greeter
                // @version 1.0
                // @match https://*.example.com/*
                // @run-at document-start
                // @grant GM_getValue
                // ==/UserScript==
                console.log('hi');
            """.trimIndent()
        ).getOrThrow()

        storageDao.upsert(UserscriptStorageEntity(installed.id, "greeted", "true"))

        val matched = repo.matchingScriptsFor("https://example.com/home", UserscriptRunAt.DOCUMENT_START)
        assertEquals(1, matched.size)
        assertEquals("true", matched.first().second["greeted"])

        assertTrue(repo.matchingScriptsFor("https://example.com/home", UserscriptRunAt.DOCUMENT_END).isEmpty())

        repo.setEnabled(installed.id, false)
        assertTrue(repo.matchingScriptsFor("https://example.com/home", UserscriptRunAt.DOCUMENT_START).isEmpty())

        val updated = repo.updateCode(
            installed.id,
            """
                // ==UserScript==
                // @name Auto Greeter v2
                // @version 2.0
                // @match https://*.example.com/*
                // @run-at document-end
                // ==/UserScript==
                console.log('v2');
            """.trimIndent()
        ).getOrThrow()
        assertEquals(installed.id, updated.id)
        assertEquals("Auto Greeter v2", updated.name)
        assertEquals("2.0", updated.version)
        assertEquals("document-end", updated.runAt)

        repo.deleteScript(installed.id)
        assertEquals(null, scriptDao.getById(installed.id))
        assertTrue(storageDao.getAllForScript(installed.id).isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun bridgeStripsForbiddenHeadersBlocksLanRedirectsAndEnforcesStorageQuota() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scriptDao = FakeUserscriptDao()
        val storageDao = FakeUserscriptStorageDao()
        val recorded = mutableListOf<okhttp3.Request>()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                recorded += req
                if (req.url.encodedPath == "/redir") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "http://192.168.86.200/admin")
                        .body(ByteArray(0).toResponseBody(null))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("ok".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            }
            .build()

        val bridge = UserscriptBridge(
            context = null,
            scope = this,
            scriptDao = scriptDao,
            storageDao = storageDao,
            client = mockClient,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher
        )

        scriptDao.upsert(
            UserscriptEntity(
                id = "s-sec",
                name = "Security Checks",
                code = "console.log(0);",
                matchesJson = UserscriptParser.encodeStringList(listOf("<all_urls>")),
                grantsJson = UserscriptParser.encodeStringList(listOf("GM_xmlhttpRequest", "GM_setValue")),
                enabled = true
            )
        )

        var headerJs: String? = null
        bridge.handleXmlHttpRequest(
            scriptId = "s-sec",
            reqId = "req-hdr",
            requestJson = """
                {
                  "method": "POST",
                  "url": "https://api.example.com/submit",
                  "data": "hello",
                  "headers": {
                    "Cookie": "secret=1",
                    "Origin": "https://evil.org",
                    "Referer": "https://evil.org",
                    "Host": "spoof.example",
                    "Sec-Fetch-Site": "cross-site",
                    "Content-Type": "application/json",
                    "X-Custom": "swiftfox"
                  }
                }
            """.trimIndent(),
            pageUrl = "https://public.example.com/",
            bridgeToken = "tok"
        ) { headerJs = it }
        advanceUntilIdle()
        assertNotNull(headerJs)
        assertTrue(headerJs!!.contains("\"onload\""))
        val sentReq = recorded.single()
        assertEquals(null, sentReq.header("Cookie"))
        assertEquals(null, sentReq.header("Origin"))
        assertEquals(null, sentReq.header("Referer"))
        assertEquals(null, sentReq.header("Sec-Fetch-Site"))
        assertEquals("swiftfox", sentReq.header("X-Custom"))
        assertEquals(0, sentReq.headers.values("Content-Type").size)
        assertEquals("application/json", sentReq.body?.contentType()?.let { "${it.type}/${it.subtype}" })

        recorded.clear()
        var redirJs: String? = null
        bridge.handleXmlHttpRequest(
            scriptId = "s-sec",
            reqId = "req-redir",
            requestJson = """{"method":"GET","url":"https://api.example.com/redir"}""",
            pageUrl = "https://public.example.com/",
            bridgeToken = "tok"
        ) { redirJs = it }
        advanceUntilIdle()
        assertNotNull(redirJs)
        assertTrue(redirJs!!.contains("\"onerror\""))
        assertEquals(1, recorded.size)

        // Storage key count cap (100 keys max per script) and per-value size cap
        repeat(100) { idx ->
            bridge.handleSetValue("s-sec", "k$idx", "$idx", pageUrl = "https://example.com/")
        }
        bridge.handleSetValue("s-sec", "k100", "100", pageUrl = "https://example.com/")
        advanceUntilIdle()
        assertEquals(100, storageDao.getAllForScript("s-sec").size)
        assertEquals(null, storageDao.getValue("s-sec", "k100"))

        val oversized = "\"" + "x".repeat(300_000) + "\""
        bridge.handleSetValue("s-sec", "k0", oversized, pageUrl = "https://example.com/")
        advanceUntilIdle()
        assertEquals("0", storageDao.getValue("s-sec", "k0")?.valueJson)
    }

    private class FakeUserscriptDao : UserscriptDao {
        private val items = MutableStateFlow<List<UserscriptEntity>>(emptyList())

        override fun observeAll(): Flow<List<UserscriptEntity>> = items

        override suspend fun getAll(): List<UserscriptEntity> = items.value

        override suspend fun getEnabled(): List<UserscriptEntity> = items.value.filter { it.enabled }

        override suspend fun getById(id: String): UserscriptEntity? = items.value.firstOrNull { it.id == id }

        override suspend fun upsert(script: UserscriptEntity) {
            items.value = items.value.filterNot { it.id == script.id } + script
        }

        override suspend fun deleteById(id: String) {
            items.value = items.value.filterNot { it.id == id }
        }
    }

    private class FakeUserscriptStorageDao : UserscriptStorageDao {
        private val entries = mutableListOf<UserscriptStorageEntity>()

        override suspend fun getAllForScript(scriptId: String): List<UserscriptStorageEntity> =
            entries.filter { it.scriptId == scriptId }

        override suspend fun getValue(scriptId: String, key: String): UserscriptStorageEntity? =
            entries.firstOrNull { it.scriptId == scriptId && it.storageKey == key }

        override suspend fun upsert(entry: UserscriptStorageEntity) {
            entries.removeAll { it.scriptId == entry.scriptId && it.storageKey == entry.storageKey }
            entries.add(entry)
        }

        override suspend fun deleteKey(scriptId: String, key: String) {
            entries.removeAll { it.scriptId == scriptId && it.storageKey == key }
        }

        override suspend fun deleteAllForScript(scriptId: String) {
            entries.removeAll { it.scriptId == scriptId }
        }
    }
}
