package app.overbay.swiftfox.features.userscripts

import android.content.Context
import app.overbay.swiftfox.data.db.UserscriptDao
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.UserscriptStorageDao
import app.overbay.swiftfox.data.db.UserscriptStorageEntity
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Generates WebView injection scripts and handles `GM_*` persistence and `GM_xmlhttpRequest`
 * bridge callbacks with explicit `@grant` and HTTP/HTTPS enforcement.
 */
class UserscriptBridge internal constructor(
    private val context: Context?,
    private val scope: CoroutineScope,
    private val scriptDao: UserscriptDao,
    private val storageDao: UserscriptStorageDao,
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher
) {
    constructor(
        context: Context?,
        scope: CoroutineScope,
        scriptDao: UserscriptDao,
        storageDao: UserscriptStorageDao,
        client: OkHttpClient = OkHttpClient()
    ) : this(context, scope, scriptDao, storageDao, client, Dispatchers.IO)

    private val json = Json { ignoreUnknownKeys = true }

    private val gmShimJs: String by lazy {
        val ctx = context ?: return@lazy ""
        runCatching {
            ctx.assets.open("js/gm_shim.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
    }

    fun buildInjectionScript(
        scriptsWithStorage: List<Pair<UserscriptEntity, Map<String, String>>>,
        runAt: UserscriptRunAt
    ): String {
        if (scriptsWithStorage.isEmpty()) return ""

        return buildString {
            if (gmShimJs.isNotBlank()) {
                append("if (typeof window.__swiftfoxGmRuntime === 'undefined') {\n")
                append(gmShimJs)
                if (!gmShimJs.endsWith('\n')) append('\n')
                append("}\n")
            }

            scriptsWithStorage.forEach { (script, storage) ->
                val matches = UserscriptParser.decodeStringList(script.matchesJson)
                val excludes = UserscriptParser.decodeStringList(script.excludesJson)
                val grants = UserscriptParser.decodeStringList(script.grantsJson)

                val scriptIdLit = escapeJsLineTerminators(json.encodeToString(script.id))
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

                val invocation = buildString {
                    append("(function() {\n")
                    append("  try {\n")
                    append("    if (typeof window.__swiftfoxGmRuntime === 'undefined') return;\n")
                    append("    const api = window.__swiftfoxGmRuntime.createApi(")
                    append(scriptIdLit).append(", ")
                    append(metaJson).append(", ")
                    append(storageJson).append(", ")
                    append(grantsJson).append(");\n")
                    append("    const { GM_info, GM_addStyle, GM_getValue, GM_setValue, GM_deleteValue, GM_listValues, GM_xmlhttpRequest, GM } = api;\n")
                    append(script.code)
                    append("\n  } catch (e) {\n")
                    append("    console.error('[Swiftfox Userscript]', ").append(scriptNameLit).append(", e);\n")
                    append("  }\n")
                    append("})();\n")
                }

                if (runAt == UserscriptRunAt.DOCUMENT_IDLE) {
                    append("setTimeout(function() {\n")
                    append(invocation)
                    append("}, 0);\n")
                } else {
                    append(invocation)
                }
            }
        }
    }

    fun handleSetValue(scriptId: String, key: String, valueJson: String) {
        if (scriptId.isBlank() || key.isEmpty()) return
        scope.launch(ioDispatcher) {
            storageDao.upsert(
                UserscriptStorageEntity(
                    scriptId = scriptId,
                    storageKey = key,
                    valueJson = valueJson
                )
            )
        }
    }

    fun handleDeleteValue(scriptId: String, key: String) {
        if (scriptId.isBlank() || key.isEmpty()) return
        scope.launch(ioDispatcher) {
            storageDao.deleteKey(scriptId, key)
        }
    }

    fun handleXmlHttpRequest(
        scriptId: String,
        reqId: String,
        requestJson: String,
        onJsCallback: (String) -> Unit
    ) {
        if (scriptId.isBlank() || reqId.isBlank()) return

        scope.launch(ioDispatcher) {
            val script = scriptDao.getById(scriptId)
            if (script == null || !script.enabled) {
                dispatchResolveXhr(
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(statusText = "Userscript missing or disabled", error = "Permission denied"),
                    onJsCallback = onJsCallback
                )
                return@launch
            }

            val grants = UserscriptParser.decodeStringList(script.grantsJson)
            val hasXhrGrant = grants.any { it == "GM_xmlhttpRequest" || it == "GM.xmlHttpRequest" }
            if (!hasXhrGrant) {
                dispatchResolveXhr(
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(statusText = "GM_xmlhttpRequest grant required", error = "Permission denied"),
                    onJsCallback = onJsCallback
                )
                return@launch
            }

            val reqObj = runCatching { json.parseToJsonElement(requestJson).jsonObject }.getOrNull()
            if (reqObj == null) {
                dispatchResolveXhr(
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

            val method = reqObj["method"]?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.isNotEmpty() }
                ?: "GET"
            val timeoutMs = (reqObj["timeout"]?.jsonPrimitive?.longOrNull ?: DEFAULT_XHR_TIMEOUT_MS)
                .coerceIn(MIN_XHR_TIMEOUT_MS, MAX_XHR_TIMEOUT_MS)
            val data = reqObj["data"]?.jsonPrimitive?.contentOrNull
            val headersMap = runCatching {
                reqObj["headers"]?.jsonObject?.mapNotNull { (name, value) ->
                    val strVal = value.jsonPrimitive.contentOrNull
                    if (name.isNotBlank() && strVal != null) name to strVal else null
                }?.toMap().orEmpty()
            }.getOrDefault(emptyMap())

            val contentTypeHeader = headersMap.entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value
            val requestBody: RequestBody? = when {
                data != null -> data.toRequestBody((contentTypeHeader ?: "text/plain; charset=utf-8").toMediaTypeOrNull())
                requiresRequestBody(method) -> ByteArray(0).toRequestBody(null)
                else -> null
            }

            val requestBuilder = Request.Builder().url(httpUrl).method(method, requestBody)
            headersMap.forEach { (headerName, headerValue) ->
                runCatching { requestBuilder.addHeader(headerName, headerValue) }
            }

            val configuredClient = client.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()

            try {
                val payload = configuredClient.newCall(requestBuilder.build()).execute().use { response ->
                    val responseHeaders = response.headers.joinToString("\r\n") { (name, value) -> "$name: $value" }
                    val responseText = response.body?.string().orEmpty()
                    buildJsonObject {
                        put("status", response.code)
                        put("statusText", response.message)
                        put("responseText", responseText)
                        put("responseHeaders", responseHeaders)
                        put("finalUrl", response.request.url.toString())
                        put("readyState", 4)
                    }
                }
                dispatchResolveXhr(
                    reqId = reqId,
                    eventName = "onload",
                    payload = payload,
                    onJsCallback = onJsCallback
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                dispatchResolveXhr(
                    reqId = reqId,
                    eventName = "ontimeout",
                    payload = errorPayload(
                        statusText = "Request timed out",
                        finalUrl = httpUrl.toString(),
                        error = e.message ?: "Timed out"
                    ),
                    onJsCallback = onJsCallback
                )
            } catch (e: IOException) {
                dispatchResolveXhr(
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(
                        statusText = e.message ?: "Network error",
                        finalUrl = httpUrl.toString(),
                        error = e.message ?: "Network error"
                    ),
                    onJsCallback = onJsCallback
                )
            } catch (e: Throwable) {
                dispatchResolveXhr(
                    reqId = reqId,
                    eventName = "onerror",
                    payload = errorPayload(
                        statusText = e.message ?: "Request failed",
                        finalUrl = httpUrl.toString(),
                        error = e.message ?: "Request failed"
                    ),
                    onJsCallback = onJsCallback
                )
            }
        }
    }

    private suspend fun dispatchResolveXhr(
        reqId: String,
        eventName: String,
        payload: JsonObject,
        onJsCallback: (String) -> Unit
    ) {
        val reqIdLit = escapeJsLineTerminators(json.encodeToString(reqId))
        val eventLit = escapeJsLineTerminators(json.encodeToString(eventName))
        val payloadLit = escapeJsLineTerminators(json.encodeToString(payload.toString()))
        val js = "window.__swiftfoxGmRuntime && window.__swiftfoxGmRuntime.resolveXhr($reqIdLit, $eventLit, $payloadLit);"

        val canDispatchMain = runCatching {
            withContext(Dispatchers.Main) { true }
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            false
        }
        if (canDispatchMain) {
            withContext(Dispatchers.Main) {
                onJsCallback(js)
            }
        } else {
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

    private companion object {
        const val MIN_XHR_TIMEOUT_MS = 1_000L
        const val MAX_XHR_TIMEOUT_MS = 60_000L
        const val DEFAULT_XHR_TIMEOUT_MS = 15_000L
    }
}
