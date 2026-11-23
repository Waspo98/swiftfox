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

        assertFalse(UserscriptUrlMatcher.matches("https://example.com/", emptyList(), emptyList()))
        assertFalse(UserscriptUrlMatcher.matches("about:blank", listOf("<all_urls>"), emptyList()))
        assertFalse(UserscriptUrlMatcher.matches("file:///sdcard/test.html", listOf("<all_urls>"), emptyList()))
        assertFalse(UserscriptUrlMatcher.matches("javascript:alert(1)", listOf("<all_urls>"), emptyList()))
    }

    @Test
    fun bridgeBuildInjectionScriptWrapsApiStorageAndIdleTiming() {
        val scriptDao = FakeUserscriptDao()
        val storageDao = FakeUserscriptStorageDao()
        val bridge = UserscriptBridge(
            context = null,
            scope = kotlinx.coroutines.CoroutineScope(StandardTestDispatcher()),
            scriptDao = scriptDao,
            storageDao = storageDao
        )

        assertEquals("", bridge.buildInjectionScript(emptyList(), UserscriptRunAt.DOCUMENT_END))

        val entity = UserscriptEntity(
            id = "script-1",
            name = "Test Script",
            namespace = "swiftfox",
            version = "1.2",
            description = "Demo",
            code = "GM_addStyle('body { color: red; }');\n// trailing comment without newline",
            matchesJson = UserscriptParser.encodeStringList(listOf("https://example.com/*")),
            excludesJson = "[]",
            runAt = UserscriptRunAt.DOCUMENT_IDLE.headerValue,
            grantsJson = UserscriptParser.encodeStringList(listOf("GM_addStyle", "GM_getValue"))
        )
        val storage = mapOf(
            "count" to "42",
            "theme" to "\"amber\""
        )

        val endJs = bridge.buildInjectionScript(listOf(entity to storage), UserscriptRunAt.DOCUMENT_END)
        assertTrue(endJs.contains("window.__swiftfoxGmRuntime.createApi(\"script-1\""))
        assertTrue(endJs.contains("\"count\":42"))
        assertTrue(endJs.contains("\"theme\":\"amber\""))
        assertTrue(endJs.contains("const { GM_info, GM_addStyle, GM_getValue, GM_setValue, GM_deleteValue, GM_listValues, GM_xmlhttpRequest, GM } = api;"))
        assertTrue(endJs.contains("// trailing comment without newline\n  } catch (e)"))
        assertFalse(endJs.contains("setTimeout(function()"))

        val idleJs = bridge.buildInjectionScript(listOf(entity to storage), UserscriptRunAt.DOCUMENT_IDLE)
        assertTrue(idleJs.contains("setTimeout(function() {"))
        assertTrue(idleJs.contains("}, 0);"))
    }

    @Test
    fun bridgeHandlesStorageAndEnforcesXhrGrantsAndScheme() = runTest {
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
            ioDispatcher = dispatcher
        )

        bridge.handleSetValue("s1", "counter", "7")
        advanceUntilIdle()
        assertEquals("7", storageDao.getValue("s1", "counter")?.valueJson)

        bridge.handleDeleteValue("s1", "counter")
        advanceUntilIdle()
        assertEquals(null, storageDao.getValue("s1", "counter"))

        // Script with @grant none must be denied XHR
        scriptDao.upsert(
            UserscriptEntity(
                id = "no-xhr",
                name = "No XHR",
                code = "console.log(1);",
                grantsJson = UserscriptParser.encodeStringList(listOf("none")),
                enabled = true
            )
        )
        var deniedJs: String? = null
        bridge.handleXmlHttpRequest(
            scriptId = "no-xhr",
            reqId = "req-1",
            requestJson = """{"method":"GET","url":"https://example.com/api"}"""
        ) { deniedJs = it }
        advanceUntilIdle()
        assertNotNull(deniedJs)
        assertTrue(deniedJs!!.contains("resolveXhr(\"req-1\",\"onerror\"") || deniedJs!!.contains("resolveXhr(\"req-1\", \"onerror\""))

        // Script with GM.xmlHttpRequest must reject file:// scheme
        scriptDao.upsert(
            UserscriptEntity(
                id = "with-xhr",
                name = "With XHR",
                code = "console.log(2);",
                grantsJson = UserscriptParser.encodeStringList(listOf("GM.xmlHttpRequest")),
                enabled = true
            )
        )
        var badSchemeJs: String? = null
        bridge.handleXmlHttpRequest(
            scriptId = "with-xhr",
            reqId = "req-2",
            requestJson = """{"method":"GET","url":"file:///etc/passwd"}"""
        ) { badSchemeJs = it }
        advanceUntilIdle()
        assertNotNull(badSchemeJs)
        assertTrue(badSchemeJs!!.contains("\"onerror\""))

        // Granted script with https:// URL succeeds via OkHttp
        var okJs: String? = null
        bridge.handleXmlHttpRequest(
            scriptId = "with-xhr",
            reqId = "req-3",
            requestJson = """{"method":"POST","url":"https://api.example.com/v1","data":"ping","timeout":500}"""
        ) { okJs = it }
        advanceUntilIdle()
        assertNotNull(okJs)
        assertTrue(okJs!!.contains("\"onload\""))
        assertTrue(okJs!!.contains("200"))
        assertTrue(okJs!!.contains("X-Test: Swiftfox"))
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
