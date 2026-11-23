package app.overbay.swiftfox.features.userscripts

import android.content.Context
import app.overbay.swiftfox.data.db.UserscriptDao
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.UserscriptStorageDao
import app.overbay.swiftfox.data.db.UserscriptStorageEntity
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Generates WebView injection scripts and handles `GM_*` persistence and `GM_xmlhttpRequest`
 * bridge callbacks with per-session token gating, `@grant` + `@match` + `@connect` checks,
 * LAN/SSRF guards, bounded responses, and real request cancellation.
 */
class UserscriptBridge internal constructor(
    private val context: Context?,
    private val scope: CoroutineScope,
    private val scriptDao: UserscriptDao,
    private val storageDao: UserscriptStorageDao,
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val mainDispatcher: CoroutineDispatcher = ioDispatcher
) {
    constructor(
        context: Context?,
        scope: CoroutineScope,
        scriptDao: UserscriptDao,
        storageDao: UserscriptStorageDao,
        client: OkHttpClient = defaultHttpClient()
    ) : this(context, scope, scriptDao, storageDao, client, Dispatchers.IO, Dispatchers.Main)

    private val json = Json { ignoreUnknownKeys = true }
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val abortedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val storageLocks = ConcurrentHashMap<String, Mutex>()

    private val gmShimJs: String by lazy {
        val ctx = context ?: return@lazy ""
        runCatching {
            ctx.assets.open("js/gm_shim.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
    }

    fun buildShimInitScript(
        bridgeToken: String = "",
        runtimeKey: String = DEFAULT_RUNTIME_KEY
    ): String {
        if (gmShimJs.isBlank()) return ""
        val tokenLit = escapeJsLineTerminators(json.encodeToString(bridgeToken))
        val runtimeKeyLit = escapeJsLineTerminators(json.encodeToString(runtimeKey))
        return buildString {
            append("if (!Object.prototype.hasOwnProperty.call(window, ").append(runtimeKeyLit).append(")) {\n")
            append("  ").append(gmShimJs.trimEnd().removeSuffix(";")).append("(")
            append(runtimeKeyLit).append(", ").append(tokenLit).append(");\n")
            append("}\n")
        }
    }

    fun buildPerScriptSnippets(
        scriptsWithStorage: List<Pair<UserscriptEntity, Map<String, String>>>,
        runAt: UserscriptRunAt,
        bridgeToken: String = "",
        runtimeKey: String = DEFAULT_RUNTIME_KEY,
        includeUrlGuard: Boolean = false
    ): List<String> {
        if (scriptsWithStorage.isEmpty()) return emptyList()
        val tokenLit = escapeJsLineTerminators(json.encodeToString(bridgeToken))
        val runtimeKeyLit = escapeJsLineTerminators(json.encodeToString(runtimeKey))

        return scriptsWithStorage.map { (script, storage) ->
            val matches = UserscriptParser.decodeStringList(script.matchesJson)
            val excludes = UserscriptParser.decodeStringList(script.excludesJson)
            val grants = UserscriptParser.decodeStringList(script.grantsJson)

            val scriptIdLit = escapeJsLineTerminators(json.encodeToString(script.id))
            val runKeyLit = escapeJsLineTerminators(json.encodeToString("${script.id}:${runAt.headerValue}"))
            val scriptNameLit = escapeJsLineTerminators(json.encodeToString(script.name))
            val metaJson = escapeJsLineTerminators(
                buildJsonObject {
                    put("id", script.id)
                    put("name", script.name)
                    put("namespace", script.namespace)
                    put("version", script.version)
                    put("description", script.description)
                    put("runAt", script.runAt)
                    putJsonArray("matches") { matches.forEach { add(it) } }
                    putJsonArray("excludes") { excludes.forEach { add(it) } }
                    putJsonArray("grants") { grants.forEach { add(it) } }
                }.toString()
            )
            val storageJson = escapeJsLineTerminators(
                buildJsonObject {
                    storage.forEach { (key, rawValueJson) ->
                        val parsed = runCatching { json.parseToJsonElement(rawValueJson) }
                            .getOrElse { JsonPrimitive(rawValueJson) }
                        put(key, parsed)
                    }
                }.toString()
            )
            val grantsJson = escapeJsLineTerminators(
                buildJsonArray { grants.forEach { add(it) } }.toString()
            )
            val urlGuardJs = if (includeUrlGuard) {
                buildJsUrlGuardClause(matches, excludes)
            } else {
                ""
            }

            val invocation = buildString {
                append("(function() {\n")
                append("  try {\n")
                append("    var __sfRt = window[").append(runtimeKeyLit).append("];\n")
                append("    if (!__sfRt || typeof __sfRt.createApi !== 'function') return;\n")
                if (urlGuardJs.isNotEmpty()) {
                    append(urlGuardJs)
                }
                append("    var __sfRan = window.__swiftfoxRanUserscripts || (window.__swiftfoxRanUserscripts = Object.create(null));\n")
                append("    if (__sfRan[").append(runKeyLit).append("]) return;\n")
                append("    __sfRan[").append(runKeyLit).append("] = true;\n")
                append("    var __sfApi = __sfRt.createApi(")
                append(tokenLit).append(", ")
                append(scriptIdLit).append(", ")
                append(metaJson).append(", ")
                append(storageJson).append(", ")
                append(grantsJson).append(");\n")
                append("    (function(GM_info, GM_addStyle, GM_getValue, GM_setValue, GM_deleteValue, GM_listValues, GM_xmlhttpRequest, GM, unsafeWindow) {\n")
                append(script.code)
                append("\n    }).call(window, __sfApi.GM_info, __sfApi.GM_addStyle, __sfApi.GM_getValue, __sfApi.GM_setValue, __sfApi.GM_deleteValue, __sfApi.GM_listValues, __sfApi.GM_xmlhttpRequest, __sfApi.GM, __sfApi.unsafeWindow);\n")
                append("  } catch (e) {\n")
                append("    console.error('[Swiftfox Userscript]', ").append(scriptNameLit).append(", e);\n")
                append("  }\n")
                append("})();\n")
            }

            if (runAt == UserscriptRunAt.DOCUMENT_IDLE) {
                buildString {
                    append("setTimeout(function() {\n")
                    append(invocation)
                    append("}, 0);\n")
                }
            } else {
                invocation
            }
        }
    }

    private fun buildJsUrlGuardClause(matches: List<String>, excludes: List<String>): String {
        val compiledMatches = matches.mapNotNull { UserscriptUrlMatcher.compileJsCheck(it) }
        val compiledExcludes = excludes.mapNotNull { UserscriptUrlMatcher.compileJsCheck(it) }
        if (compiledMatches.isEmpty()) return "    return;\n"
        fun encodeChecks(checks: List<UserscriptUrlMatcher.JsUrlCheck>): String {
            return buildJsonArray {
                checks.forEach { c ->
                    add(
                        buildJsonObject {
                            put("r", c.regexBody)
                            put("f", c.flags)
                            put("full", c.matchFullHref)
                        }
                    )
                }
            }.toString().let(::escapeJsLineTerminators)
        }
        return buildString {
            append("    var __sfHref = String(window.location && window.location.href || '');\n")
            append("    if (!/^https?:\\/\\//i.test(__sfHref)) return;\n")
            append("    var __sfNoHash = __sfHref.split('#')[0];\n")
            append("    var __sfMatch = function(list) {\n")
            append("      for (var i = 0; i < list.length; i++) {\n")
            append("        try {\n")
            append("          var re = new RegExp(list[i].r, list[i].f);\n")
            append("          if (re.test(list[i].full ? __sfHref : __sfNoHash)) return true;\n")
            append("        } catch (_e) {}\n")
            append("      }\n")
            append("      return false;\n")
            append("    };\n")
            if (compiledExcludes.isNotEmpty()) {
                append("    if (__sfMatch(").append(encodeChecks(compiledExcludes)).append(")) return;\n")
            }
            append("    if (!__sfMatch(").append(encodeChecks(compiledMatches)).append(")) return;\n")
        }
    }

    fun handleSetValue(
        scriptId: String,
        key: String,
        valueJson: String,
        pageUrl: String? = null
    ) {
        if (scriptId.isBlank() || key.isEmpty() || key == "__proto__") return
        val valBytes = valueJson.toByteArray(Charsets.UTF_8).size
        if (key.length > MAX_STORAGE_KEY_LEN || valBytes > MAX_STORAGE_VALUE_LEN) return

        scope.launch(ioDispatcher) {
            val script = scriptDao.getById(scriptId) ?: return@launch
            if (!script.enabled) return@launch
            val grants = UserscriptParser.decodeStringList(script.grantsJson)
            if (!hasStorageGrant(grants, underscoreName = "GM_setValue", dotName = "GM.setValue")) return@launch
            if (!matchesPageIfProvided(script, pageUrl)) return@launch

            val lock = storageLocks.computeIfAbsent(scriptId) { Mutex() }
            lock.withLock {
                val existing = storageDao.getAllForScript(scriptId)
                val hasKey = existing.any { it.storageKey == key }
                if (!hasKey && existing.size >= MAX_STORAGE_KEYS_PER_SCRIPT) {
                    return@withLock
                }
                val keyBytes = key.toByteArray(Charsets.UTF_8).size
                val otherBytes = existing
                    .filter { it.storageKey != key }
                    .sumOf {
                        it.storageKey.toByteArray(Charsets.UTF_8).size +
                            it.valueJson.toByteArray(Charsets.UTF_8).size
                    }
                if (otherBytes + keyBytes + valBytes > MAX_STORAGE_TOTAL_BYTES_PER_SCRIPT) {
                    return@withLock
                }

                storageDao.upsert(
                    UserscriptStorageEntity(
                        scriptId = scriptId,
                        storageKey = key,
                        valueJson = valueJson
                    )
                )
            }
        }
    }

    fun handleDeleteValue(
        scriptId: String,
        key: String,
        pageUrl: String? = null
    ) {
        if (scriptId.isBlank() || key.isEmpty()) return
        if (key.length > MAX_STORAGE_KEY_LEN) return

        scope.launch(ioDispatcher) {
            val script = scriptDao.getById(scriptId) ?: return@launch
            if (!script.enabled) return@launch
            val grants = UserscriptParser.decodeStringList(script.grantsJson)
            if (!hasStorageGrant(grants, underscoreName = "GM_deleteValue", dotName = "GM.deleteValue")) return@launch
            if (!matchesPageIfProvided(script, pageUrl)) return@launch

            val lock = storageLocks.computeIfAbsent(scriptId) { Mutex() }
            lock.withLock {
                storageDao.deleteKey(scriptId, key)
            }
        }
    }

    fun handleAbortXmlHttpRequest(reqId: String, tabId: String = "") {
        if (reqId.isBlank()) return
        val callKey = scopedCallKey(tabId, reqId)
        if (abortedKeys.size > MAX_ABORT_TOMBSTONES) {
            abortedKeys.clear()
        }
        abortedKeys.add(callKey)
        activeCalls.remove(callKey)?.cancel()
    }

    fun handleXmlHttpRequest(
        scriptId: String,
        reqId: String,
        requestJson: String,
        pageUrl: String? = null,
        bridgeToken: String = "",
        tabId: String = "",
        runtimeKey: String = DEFAULT_RUNTIME_KEY,
        onJsCallback: (String) -> Unit
    ) {
        if (scriptId.isBlank() || reqId.isBlank()) return
        val callKey = scopedCallKey(tabId, reqId)

        scope.launch(ioDispatcher) {
            if (abortedKeys.remove(callKey)) {
                dispatchResolveXhr(
                    bridgeToken = bridgeToken,
                    runtimeKey = runtimeKey,
                    reqId = reqId,
                    eventName = "onabort",
                    payload = errorPayload(statusText = "Request aborted", error = "Aborted"),
                    onJsCallback = onJsCallback
                )
                return@launch
            }

            val script = scriptDao.getById(scriptId)
            if (script == null || !script.enabled) {
                dispatchResolveXhr(
                    bridgeToken = bridgeToken,
                    runtimeKey = runtimeKey,
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(statusText = "Userscript missing or disabled", error = "Permission denied"),
                    onJsCallback = onJsCallback
                )
                return@launch
            }

            val grants = UserscriptParser.decodeStringList(script.grantsJson)
            val hasXhrGrant = !grants.contains("none") &&
                grants.any { it == "GM_xmlhttpRequest" || it == "GM.xmlHttpRequest" }
            if (!hasXhrGrant) {
                dispatchResolveXhr(
                    bridgeToken = bridgeToken,
                    runtimeKey = runtimeKey,
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(statusText = "GM_xmlhttpRequest grant required", error = "Permission denied"),
                    onJsCallback = onJsCallback
                )
                return@launch
            }

            if (!matchesPageIfProvided(script, pageUrl)) {
                dispatchResolveXhr(
                    bridgeToken = bridgeToken,
                    runtimeKey = runtimeKey,
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(statusText = "Userscript not permitted on this page", error = "Permission denied"),
                    onJsCallback = onJsCallback
                )
                return@launch
            }

            val reqObj = runCatching { json.parseToJsonElement(requestJson).jsonObject }.getOrNull()
            if (reqObj == null) {
                dispatchResolveXhr(
                    bridgeToken = bridgeToken,
                    runtimeKey = runtimeKey,
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(statusText = "Malformed request payload", error = "Bad request"),
                    onJsCallback = onJsCallback
                )
                return@launch
            }

            val rawUrl = reqObj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val httpUrl = if (UserscriptUrlMatcher.isHttpOrHttps(rawUrl)) rawUrl.toHttpUrlOrNull() else null
            if (httpUrl == null) {
                dispatchResolveXhr(
                    bridgeToken = bridgeToken,
                    runtimeKey = runtimeKey,
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(
                        statusText = "Only http:// and https:// URLs are allowed",
                        finalUrl = rawUrl,
                        error = "Invalid URL scheme"
                    ),
                    onJsCallback = onJsCallback
                )
                return@launch
            }

            val parsedMeta = UserscriptParser.parse(script.code, fallbackName = script.name)
            if (!isTargetHostPermitted(httpUrl.host, pageUrl, parsedMeta.connects)) {
                dispatchResolveXhr(
                    bridgeToken = bridgeToken,
                    runtimeKey = runtimeKey,
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(
                        statusText = "Blocked by @connect / local network policy",
                        finalUrl = httpUrl.toString(),
                        error = "Host not permitted"
                    ),
                    onJsCallback = onJsCallback
                )
                return@launch
            }

            val method = reqObj["method"]?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.uppercase(Locale.ROOT)
                ?.takeIf { it.isNotEmpty() }
                ?: "GET"
            if (method !in ALLOWED_XHR_METHODS) {
                dispatchResolveXhr(
                    bridgeToken = bridgeToken,
                    runtimeKey = runtimeKey,
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(
                        statusText = "Unsupported HTTP method: $method",
                        finalUrl = httpUrl.toString(),
                        error = "Unsupported HTTP method"
                    ),
                    onJsCallback = onJsCallback
                )
                return@launch
            }

            val timeoutMs = (reqObj["timeout"]?.jsonPrimitive?.longOrNull ?: DEFAULT_XHR_TIMEOUT_MS)
                .coerceIn(MIN_XHR_TIMEOUT_MS, MAX_XHR_TIMEOUT_MS)
            val data = reqObj["data"]?.jsonPrimitive?.contentOrNull
            if (data != null && data.toByteArray(Charsets.UTF_8).size > MAX_XHR_REQUEST_BYTES) {
                dispatchResolveXhr(
                    bridgeToken = bridgeToken,
                    runtimeKey = runtimeKey,
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(
                        statusText = "Request body exceeds 4 MB limit",
                        finalUrl = httpUrl.toString(),
                        error = "Payload too large"
                    ),
                    onJsCallback = onJsCallback
                )
                return@launch
            }

            val rawHeaders = runCatching {
                reqObj["headers"]?.jsonObject?.mapNotNull { (name, value) ->
                    val strVal = value.jsonPrimitive.contentOrNull
                    if (name.isNotBlank() && strVal != null) name to strVal else null
                }.orEmpty()
            }.getOrDefault(emptyList())
            val sanitizedHeaders = sanitizeHeaders(rawHeaders)

            val contentTypeHeader = sanitizedHeaders
                .firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }
                ?.second
            val initialBody: RequestBody? = when {
                method == "GET" || method == "HEAD" -> null
                data != null -> data.toRequestBody((contentTypeHeader ?: "text/plain; charset=utf-8").toMediaTypeOrNull())
                requiresRequestBody(method) -> ByteArray(0).toRequestBody(null)
                else -> null
            }

            val configuredClient = client.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .dns(buildGuardedDns(pageUrl, parsedMeta.connects))
                .build()

            var currentCall: Call? = null
            var currentUrl: HttpUrl = httpUrl
            try {
                var currentMethod = method
                var currentBody = initialBody
                var currentHeaders = sanitizedHeaders
                var redirectCount = 0
                var terminalPayload: JsonObject? = null

                while (true) {
                    if (abortedKeys.remove(callKey)) {
                        dispatchResolveXhr(
                            bridgeToken = bridgeToken,
                            runtimeKey = runtimeKey,
                            reqId = reqId,
                            eventName = "onabort",
                            payload = errorPayload(statusText = "Request aborted", finalUrl = currentUrl.toString(), error = "Aborted"),
                            onJsCallback = onJsCallback
                        )
                        return@launch
                    }

                    val requestBuilder = Request.Builder()
                        .url(currentUrl)
                        .method(currentMethod, currentBody)
                    val bodyHasContentType = currentBody?.contentType() != null
                    currentHeaders.forEach { (headerName, headerValue) ->
                        if (bodyHasContentType && headerName.equals("Content-Type", ignoreCase = true)) {
                            return@forEach
                        }
                        runCatching { requestBuilder.addHeader(headerName, headerValue) }
                    }

                    val call = configuredClient.newCall(requestBuilder.build())
                    currentCall = call
                    activeCalls[callKey] = call

                    var nextStepUrl: HttpUrl? = null
                    call.execute().use { response ->
                        val isRedirect = response.code in REDIRECT_STATUS_CODES
                        val location = if (isRedirect) response.header("Location") else null
                        if (isRedirect && !location.isNullOrBlank()) {
                            if (redirectCount >= MAX_XHR_REDIRECTS) {
                                throw IOException("Too many redirects")
                            }
                            val resolved = currentUrl.resolve(location)
                            if (resolved == null || (resolved.scheme != "http" && resolved.scheme != "https")) {
                                throw IOException("Invalid redirect destination")
                            }
                            if (!isTargetHostPermitted(resolved.host, pageUrl, parsedMeta.connects)) {
                                throw IOException("Redirect blocked by @connect / local network policy")
                            }
                            val sameOrigin = resolved.scheme == currentUrl.scheme &&
                                resolved.host == currentUrl.host &&
                                resolved.port == currentUrl.port
                            if (!sameOrigin) {
                                currentHeaders = currentHeaders.filterNot {
                                    it.first.equals("Authorization", ignoreCase = true)
                                }
                            }
                            if (response.code == 303 ||
                                ((response.code == 301 || response.code == 302) && currentMethod == "POST")
                            ) {
                                currentMethod = "GET"
                                currentBody = null
                                currentHeaders = currentHeaders.filterNot {
                                    it.first.equals("Content-Type", ignoreCase = true)
                                }
                            }
                            nextStepUrl = resolved
                        } else {
                            val responseHeaders = response.headers.joinToString("\r\n") { (name, value) -> "$name: $value" }
                            val responseText = readBoundedBodyString(response.body, MAX_XHR_RESPONSE_BYTES)
                            terminalPayload = buildJsonObject {
                                put("status", response.code)
                                put("statusText", response.message)
                                put("responseText", responseText)
                                put("responseHeaders", responseHeaders)
                                put("finalUrl", response.request.url.toString())
                                put("readyState", 4)
                            }
                        }
                    }

                    val next = nextStepUrl ?: break
                    redirectCount++
                    currentUrl = next
                }

                val payload = terminalPayload ?: errorPayload(
                    statusText = "Request failed",
                    finalUrl = currentUrl.toString()
                )
                dispatchResolveXhr(
                    bridgeToken = bridgeToken,
                    runtimeKey = runtimeKey,
                    reqId = reqId,
                    eventName = "onload",
                    payload = payload,
                    onJsCallback = onJsCallback
                )
            } catch (e: CancellationException) {
                currentCall?.cancel()
                throw e
            } catch (e: SocketTimeoutException) {
                if (currentCall?.isCanceled() != true) {
                    dispatchResolveXhr(
                        bridgeToken = bridgeToken,
                        runtimeKey = runtimeKey,
                        reqId = reqId,
                        eventName = "ontimeout",
                        payload = errorPayload(
                            statusText = "Request timed out",
                            finalUrl = currentUrl.toString(),
                            error = e.message ?: "Timed out"
                        ),
                        onJsCallback = onJsCallback
                    )
                }
            } catch (e: IOException) {
                if (currentCall?.isCanceled() != true) {
                    dispatchResolveXhr(
                        bridgeToken = bridgeToken,
                        runtimeKey = runtimeKey,
                        reqId = reqId,
                        eventName = "onerror",
                        payload = errorPayload(
                            statusText = e.message ?: "Network error",
                            finalUrl = currentUrl.toString(),
                            error = e.message ?: "Network error"
                        ),
                        onJsCallback = onJsCallback
                    )
                }
            } catch (e: Throwable) {
                if (currentCall?.isCanceled() != true) {
                    dispatchResolveXhr(
                        bridgeToken = bridgeToken,
                        runtimeKey = runtimeKey,
                        reqId = reqId,
                        eventName = "onerror",
                        payload = errorPayload(
                            statusText = e.message ?: "Request failed",
                            finalUrl = currentUrl.toString(),
                            error = e.message ?: "Request failed"
                        ),
                        onJsCallback = onJsCallback
                    )
                }
            } finally {
                if (currentCall != null) {
                    activeCalls.remove(callKey, currentCall)
                } else {
                    activeCalls.remove(callKey)
                }
                abortedKeys.remove(callKey)
            }
        }
    }

    private fun scopedCallKey(tabId: String, reqId: String): String = "$tabId:$reqId"

    private fun sanitizeHeaders(raw: List<Pair<String, String>>): List<Pair<String, String>> {
        return raw.filter { (name, _) ->
            val lower = name.trim().lowercase(Locale.ROOT)
            lower.isNotEmpty() &&
                lower !in FORBIDDEN_XHR_HEADERS &&
                !lower.startsWith("sec-") &&
                !lower.startsWith("proxy-")
        }
    }

    private fun buildGuardedDns(pageUrl: String?, connects: List<String>): Dns = Dns { hostname ->
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (!isLocalResolutionPermitted(hostname, pageUrl, connects) &&
            addresses.any { OmniboxEngine.isLocalOrPrivateInetAddress(it) }
        ) {
            throw UnknownHostException("Blocked local/private IP resolution for $hostname")
        }
        addresses
    }

    private fun hasStorageGrant(
        grants: List<String>,
        underscoreName: String,
        dotName: String
    ): Boolean {
        if (grants.contains("none")) return false
        if (grants.isEmpty()) return true
        return grants.contains(underscoreName) || grants.contains(dotName)
    }

    private fun matchesPageIfProvided(script: UserscriptEntity, pageUrl: String?): Boolean {
        if (pageUrl.isNullOrBlank()) return false
        val matches = UserscriptParser.decodeStringList(script.matchesJson)
        val excludes = UserscriptParser.decodeStringList(script.excludesJson)
        return UserscriptUrlMatcher.matches(pageUrl, matches, excludes)
    }

    private fun isLocalResolutionPermitted(
        targetHost: String,
        pageUrl: String?,
        connects: List<String>
    ): Boolean {
        val cleanTarget = targetHost.trim().lowercase(Locale.ROOT).removePrefix("[").removeSuffix("]").trimEnd('.')
        if (cleanTarget.isEmpty()) return false
        val pageHost = pageUrl?.takeIf { it.isNotBlank() }?.let { OmniboxEngine.extractHost(it) }.orEmpty()
        if (pageHost.isNotEmpty() && OmniboxEngine.isLocalHostOrLan(pageHost)) {
            return true
        }
        if (connects.isEmpty()) return false
        return connects.any { rule ->
            val cleanRule = rule.trim().lowercase(Locale.ROOT)
            when (cleanRule) {
                "localhost" -> cleanTarget == "localhost" || cleanTarget == "127.0.0.1" || cleanTarget == "::1"
                "self" -> false // pageHost already known not to be local here
                "*" -> false
                else -> {
                    val ruleBase = cleanRule.removePrefix("*.").removePrefix("[").removeSuffix("]").trimEnd('.')
                    OmniboxEngine.isLocalHostOrLan(ruleBase) && hostMatchesConnectRule(cleanTarget, cleanRule)
                }
            }
        }
    }

    internal fun isTargetHostPermitted(
        targetHost: String,
        pageUrl: String?,
        connects: List<String>
    ): Boolean {
        val cleanTarget = targetHost.trim().lowercase(Locale.ROOT).removePrefix("[").removeSuffix("]").trimEnd('.')
        if (cleanTarget.isEmpty()) return false

        val pageHost = pageUrl?.takeIf { it.isNotBlank() }?.let { OmniboxEngine.extractHost(it) }.orEmpty()
        val targetIsLocal = OmniboxEngine.isLocalHostOrLan(cleanTarget)
        val pageIsLocal = pageHost.isNotEmpty() && OmniboxEngine.isLocalHostOrLan(pageHost)

        if (connects.isEmpty()) {
            // Without explicit @connect, block public pages from probing localhost/LAN endpoints.
            return !targetIsLocal || pageIsLocal
        }

        val explicitlyMatchesLocalRule = connects.any { rule ->
            val cleanRule = rule.trim().lowercase(Locale.ROOT)
            when {
                cleanRule == "localhost" -> cleanTarget == "localhost" || cleanTarget == "127.0.0.1" || cleanTarget == "::1"
                cleanRule == "self" -> pageHost.isNotEmpty() && OmniboxEngine.normalizeHost(cleanTarget) == pageHost
                cleanRule == "*" -> false
                else -> hostMatchesConnectRule(cleanTarget, cleanRule)
            }
        }

        if (targetIsLocal && !pageIsLocal && !explicitlyMatchesLocalRule) {
            return false
        }

        return connects.any { rule ->
            val cleanRule = rule.trim().lowercase(Locale.ROOT)
            when (cleanRule) {
                "*" -> true
                "self" -> pageHost.isNotEmpty() && OmniboxEngine.normalizeHost(cleanTarget) == pageHost
                "localhost" -> cleanTarget == "localhost" || cleanTarget == "127.0.0.1" || cleanTarget == "::1"
                else -> hostMatchesConnectRule(cleanTarget, cleanRule)
            }
        }
    }

    private fun hostMatchesConnectRule(targetHost: String, rule: String): Boolean {
        val cleanRule = rule.removePrefix("*.").removePrefix("[").removeSuffix("]").trimEnd('.')
        if (cleanRule.isEmpty()) return false
        return targetHost == cleanRule || targetHost.endsWith(".$cleanRule")
    }

    private fun readBoundedBodyString(body: ResponseBody?, maxBytes: Int): String {
        if (body == null) return ""
        val contentLength = body.contentLength()
        if (contentLength > maxBytes) {
            throw IOException("Response exceeds ${maxBytes / (1024 * 1024)} MB limit")
        }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        body.byteStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw IOException("Response exceeds ${maxBytes / (1024 * 1024)} MB limit")
                }
                out.write(buffer, 0, read)
            }
        }
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        return out.toString(charset.name())
    }

    private suspend fun dispatchResolveXhr(
        bridgeToken: String,
        runtimeKey: String,
        reqId: String,
        eventName: String,
        payload: JsonObject,
        onJsCallback: (String) -> Unit
    ) {
        val runtimeKeyLit = escapeJsLineTerminators(json.encodeToString(runtimeKey))
        val tokenLit = escapeJsLineTerminators(json.encodeToString(bridgeToken))
        val reqIdLit = escapeJsLineTerminators(json.encodeToString(reqId))
        val eventLit = escapeJsLineTerminators(json.encodeToString(eventName))
        val payloadLit = escapeJsLineTerminators(json.encodeToString(payload.toString()))
        val js = "(function(){var r=window[$runtimeKeyLit];if(r&&typeof r.resolveXhr==='function'){r.resolveXhr($tokenLit,$reqIdLit,$eventLit,$payloadLit);}})();"

        withContext(mainDispatcher) {
            onJsCallback(js)
        }
    }

    private fun errorPayload(
        statusText: String,
        finalUrl: String = "",
        error: String = statusText
    ): JsonObject = buildJsonObject {
        put("status", 0)
        put("statusText", statusText)
        put("responseText", "")
        put("responseHeaders", "")
        put("finalUrl", finalUrl)
        put("readyState", 4)
        put("error", error)
    }

    private fun requiresRequestBody(method: String): Boolean = when (method) {
        "POST", "PUT", "PATCH", "PROPPATCH", "REPORT" -> true
        else -> false
    }

    private fun escapeJsLineTerminators(raw: String): String =
        raw.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

    companion object {
        const val DEFAULT_RUNTIME_KEY = "__swiftfoxGmRuntime"

        private const val MIN_XHR_TIMEOUT_MS = 1_000L
        private const val MAX_XHR_TIMEOUT_MS = 60_000L
        private const val DEFAULT_XHR_TIMEOUT_MS = 15_000L
        private const val MAX_XHR_REQUEST_BYTES = 4 * 1024 * 1024
        private const val MAX_XHR_RESPONSE_BYTES = 4 * 1024 * 1024
        private const val MAX_XHR_REDIRECTS = 5
        private const val MAX_STORAGE_KEY_LEN = 256
        private const val MAX_STORAGE_VALUE_LEN = 262_144
        private const val MAX_STORAGE_KEYS_PER_SCRIPT = 100
        private const val MAX_STORAGE_TOTAL_BYTES_PER_SCRIPT = 1024 * 1024
        private const val MAX_ABORT_TOMBSTONES = 512

        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        private val ALLOWED_XHR_METHODS = setOf("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        private val FORBIDDEN_XHR_HEADERS = setOf(
            "host",
            "content-length",
            "transfer-encoding",
            "connection",
            "keep-alive",
            "te",
            "trailer",
            "upgrade",
            "cookie",
            "cookie2",
            "origin",
            "referer",
            "proxy-authorization",
            "proxy-connection"
        )

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
