package app.overbay.swiftfox.engine

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.features.adblock.CosmeticPayload
import app.overbay.swiftfox.features.darkreader.DarkReaderInjector
import app.overbay.swiftfox.features.darkreader.ResolvedWebDarkState
import app.overbay.swiftfox.features.userscripts.UserscriptBridge
import app.overbay.swiftfox.features.userscripts.UserscriptRunAt
import app.overbay.swiftfox.features.zapper.ZapperController
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.Locale

/**
 * Coordinates WebView script injection across `addDocumentStartJavaScript`, `onPageStarted`,
 * and `onPageFinished` for cosmetic rules, generic DOM cosmetic scanning, element zapper CSS,
 * Dark Reader, and syntax-isolated userscripts.
 */
object ScriptInjector {

    private val json = Json
    private val authorityFallbackRegex = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*)://([^/?#\\]+)""")

    fun extractOrigin(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull()
        if (uri != null && !uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
            val scheme = uri.scheme.lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host.lowercase(Locale.ROOT)
            val port = uri.port
            val isDefaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
            return if (port > 0 && !isDefaultPort) "$scheme://$host:$port" else "$scheme://$host"
        }

        val m = authorityFallbackRegex.find(trimmed) ?: return null
        val scheme = m.groupValues[1].lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val hostPort = m.groupValues[2].substringAfterLast('@')
        if (hostPort.isEmpty()) return null

        val host: String
        val port: Int
        if (hostPort.startsWith("[")) {
            val closeIdx = hostPort.indexOf(']')
            if (closeIdx <= 1) return null
            host = hostPort.substring(0, closeIdx + 1).lowercase(Locale.ROOT)
            val after = hostPort.substring(closeIdx + 1)
            port = if (after.startsWith(":")) after.substring(1).toIntOrNull() ?: -1 else -1
        } else {
            val colonIdx = hostPort.lastIndexOf(':')
            if (colonIdx > 0) {
                host = hostPort.substring(0, colonIdx).lowercase(Locale.ROOT)
                port = hostPort.substring(colonIdx + 1).toIntOrNull() ?: -1
            } else {
                host = hostPort.lowercase(Locale.ROOT)
                port = -1
            }
        }
        if (host.isBlank()) return null
        val isDefaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
        return if (port in 1..65535 && !isDefaultPort) "$scheme://$host:$port" else "$scheme://$host"
    }

    fun buildCssStyleScript(styleId: String, css: String): String {
        val idLit = json.encodeToString(styleId)
        val cssLit = json.encodeToString(css)
        return """
            (function() {
                if (!location || (location.protocol !== 'http:' && location.protocol !== 'https:')) return;
                var id = $idLit;
                var css = $cssLit;
                function attach() {
                    var existing = document.getElementById(id);
                    if (!css) {
                        if (existing) existing.textContent = '';
                        return true;
                    }
                    if (!existing) {
                        var target = document.head || document.documentElement;
                        if (!target) return false;
                        existing = document.createElement('style');
                        existing.id = id;
                        existing.type = 'text/css';
                        target.appendChild(existing);
                    }
                    existing.textContent = css;
                    return true;
                }
                if (!attach()) {
                    if (typeof MutationObserver === 'function') {
                        var obs = new MutationObserver(function() {
                            if (attach()) obs.disconnect();
                        });
                        obs.observe(document, { childList: true, subtree: true });
                    }
                    document.addEventListener('DOMContentLoaded', attach, { once: true });
                }
            })();
        """.trimIndent()
    }

    fun buildCosmeticScriptletScript(injectedScript: String): String {
        if (injectedScript.isBlank()) return ""
        return """
            (function() {
                if (window.__swiftfoxCosmeticScriptRan) return;
                window.__swiftfoxCosmeticScriptRan = true;
                try {
                    $injectedScript
                } catch (_e) {}
            })();
        """.trimIndent()
    }

    fun buildGenericCosmeticScanScript(bridgeToken: String): String {
        val tokenLit = json.encodeToString(bridgeToken)
        return """
            (function() {
                if (window !== window.top) return;
                if (!location || (location.protocol !== 'http:' && location.protocol !== 'https:')) return;
                var token = $tokenLit;
                var ch = window.SwiftfoxNativeChannel;
                if (!token || !ch || typeof ch.postMessage !== 'function') return;
                var seenClasses = Object.create(null);
                var seenIds = Object.create(null);
                var maxTokens = 1500;
                var classCount = 0;
                var idCount = 0;
                function scanAndReport() {
                    var newClasses = [];
                    var newIds = [];
                    var els = document.querySelectorAll('[class],[id]');
                    for (var i = 0; i < els.length; i++) {
                        var el = els[i];
                        var id = el.id;
                        if (id && id.length <= 128 && !seenIds[id] && idCount < maxTokens) {
                            seenIds[id] = true;
                            idCount++;
                            newIds.push(id);
                        }
                        var cl = el.classList;
                        if (cl && cl.length > 0) {
                            for (var j = 0; j < cl.length; j++) {
                                var cls = cl[j];
                                if (cls && cls.length <= 128 && !seenClasses[cls] && classCount < maxTokens) {
                                    seenClasses[cls] = true;
                                    classCount++;
                                    newClasses.push(cls);
                                }
                            }
                        }
                    }
                    if (newClasses.length > 0 || newIds.length > 0) {
                        try {
                            ch.postMessage(JSON.stringify({
                                channel: 'cosmetic',
                                token: token,
                                classesJson: JSON.stringify(newClasses),
                                idsJson: JSON.stringify(newIds)
                            }));
                        } catch (_e) {}
                    }
                }
                scanAndReport();
                if (!window.__swiftfoxCosmeticScanScheduled) {
                    window.__swiftfoxCosmeticScanScheduled = true;
                    setTimeout(scanAndReport, 1200);
                }
            })();
        """.trimIndent()
    }

    fun buildPrepaintDarkCss(amoled: Boolean): String {
        val bg = if (amoled) "#000000" else "#18161b"
        return "html{background-color:$bg;color-scheme:dark;}"
    }

    fun registerDocumentStartScripts(
        webView: WebView,
        previousHandlers: List<ScriptHandler>,
        origin: String,
        cosmeticCss: String,
        zappedCss: String,
        gmShimInitJs: String,
        documentStartUserscriptSnippets: List<String>,
        prepaintDarkCss: String = ""
    ): List<ScriptHandler> {
        previousHandlers.forEach { runCatching { it.remove() } }
        if (origin.isBlank() || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return emptyList()
        }

        val originLit = json.encodeToString(origin)
        val allowedOrigins = setOf(origin)
        val handlers = mutableListOf<ScriptHandler>()

        fun registerGuarded(rawJs: String) {
            if (rawJs.isBlank()) return
            val wrapped = buildString(rawJs.length + 180) {
                append("(function(){\n")
                append("if (window !== window.top) return;\n")
                append("if (!window.location || window.location.origin !== ").append(originLit).append(") return;\n")
                append(rawJs)
                append("\n})();")
            }
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(webView, wrapped, allowedOrigins)
            }.getOrNull()?.let { handlers.add(it) }
        }

        val cssBlock = buildString {
            if (prepaintDarkCss.isNotBlank()) {
                append(buildCssStyleScript("swiftfox-prepaint-dark", prepaintDarkCss)).append('\n')
            }
            if (cosmeticCss.isNotBlank()) {
                append(buildCssStyleScript("swiftfox-cosmetic", cosmeticCss)).append('\n')
            }
            if (zappedCss.isNotBlank()) {
                append(buildCssStyleScript("swiftfox-zapped", zappedCss)).append('\n')
            }
        }
        if (cssBlock.isNotBlank()) {
            registerGuarded(cssBlock)
        }

        if (documentStartUserscriptSnippets.isNotEmpty()) {
            if (gmShimInitJs.isNotBlank()) {
                registerGuarded(gmShimInitJs)
            }
            // Register each userscript separately so a syntax error in one never blocks siblings
            documentStartUserscriptSnippets.forEach { snippet ->
                registerGuarded(snippet)
            }
        }

        return handlers
    }

    fun buildAppendGenericCosmeticCssScript(css: String): String {
        val cssLit = json.encodeToString(css)
        return """
            (function(){
              try {
                var css = $cssLit;
                if (!css) return;
                var id = 'swiftfox-cosmetic-generic';
                var el = document.getElementById(id);
                if (!el) {
                  el = document.createElement('style');
                  el.id = id;
                  el.type = 'text/css';
                  (document.head || document.documentElement).appendChild(el);
                }
                if (el.textContent.indexOf(css) === -1) {
                  el.textContent += '\n' + css;
                }
              } catch (e) {}
            })();
        """.trimIndent()
    }

    fun injectOnPageStarted(
        webView: WebView,
        tokens: BridgeTokenSet,
        cosmetic: CosmeticPayload,
        zappedRules: List<ZappedRuleEntity>,
        zapperController: ZapperController,
        darkState: ResolvedWebDarkState,
        darkReaderInjector: DarkReaderInjector,
        docStartUserscripts: List<Pair<UserscriptEntity, Map<String, String>>>,
        userscriptBridge: UserscriptBridge,
        isDestroyed: () -> Boolean = { false }
    ) {
        if (isDestroyed()) return
        webView.evaluateJavascript(buildCssStyleScript("swiftfox-cosmetic", cosmetic.css), null)
        webView.evaluateJavascript(buildCssStyleScript("swiftfox-cosmetic-generic", ""), null)
        if (cosmetic.injectedScript.isNotBlank()) {
            webView.evaluateJavascript(buildCosmeticScriptletScript(cosmetic.injectedScript), null)
        }

        webView.evaluateJavascript(zapperController.buildApplyZappedCssScript(zappedRules), null)

        if (docStartUserscripts.isNotEmpty()) {
            val shimJs = userscriptBridge.buildShimInitScript(tokens.gmToken, tokens.gmKey)
            if (shimJs.isNotBlank()) {
                webView.evaluateJavascript(shimJs, null)
            }
            userscriptBridge.buildPerScriptSnippets(
                scriptsWithStorage = docStartUserscripts,
                runAt = UserscriptRunAt.DOCUMENT_START,
                bridgeToken = tokens.gmToken,
                runtimeKey = tokens.gmKey
            ).forEach { snippet ->
                webView.evaluateJavascript(snippet, null)
            }
        }

        injectDarkReaderFastOrFull(webView, tokens, darkState, darkReaderInjector, isDestroyed)
    }

    fun injectOnPageFinished(
        webView: WebView,
        tokens: BridgeTokenSet,
        cosmetic: CosmeticPayload,
        runGenericCosmeticScan: Boolean,
        zappedRules: List<ZappedRuleEntity>,
        zapperController: ZapperController,
        darkState: ResolvedWebDarkState,
        darkReaderInjector: DarkReaderInjector,
        docEndUserscripts: List<Pair<UserscriptEntity, Map<String, String>>>,
        docIdleUserscripts: List<Pair<UserscriptEntity, Map<String, String>>>,
        userscriptBridge: UserscriptBridge,
        isDestroyed: () -> Boolean = { false }
    ) {
        if (isDestroyed()) return
        if (cosmetic.css.isNotBlank()) {
            webView.evaluateJavascript(buildCssStyleScript("swiftfox-cosmetic", cosmetic.css), null)
        }
        if (cosmetic.injectedScript.isNotBlank()) {
            webView.evaluateJavascript(buildCosmeticScriptletScript(cosmetic.injectedScript), null)
        }
        if (runGenericCosmeticScan && !cosmetic.generichide) {
            webView.evaluateJavascript(buildGenericCosmeticScanScript(tokens.cosmeticToken), null)
        }
        webView.evaluateJavascript(zapperController.buildApplyZappedCssScript(zappedRules), null)

        injectDarkReaderFastOrFull(webView, tokens, darkState, darkReaderInjector, isDestroyed)

        val hasAnyUserscripts = docEndUserscripts.isNotEmpty() || docIdleUserscripts.isNotEmpty()
        if (hasAnyUserscripts) {
            val shimJs = userscriptBridge.buildShimInitScript(tokens.gmToken, tokens.gmKey)
            if (shimJs.isNotBlank()) {
                webView.evaluateJavascript(shimJs, null)
            }
        }
        if (docEndUserscripts.isNotEmpty()) {
            userscriptBridge.buildPerScriptSnippets(
                scriptsWithStorage = docEndUserscripts,
                runAt = UserscriptRunAt.DOCUMENT_END,
                bridgeToken = tokens.gmToken,
                runtimeKey = tokens.gmKey
            ).forEach { snippet ->
                webView.evaluateJavascript(snippet, null)
            }
        }
        if (docIdleUserscripts.isNotEmpty()) {
            userscriptBridge.buildPerScriptSnippets(
                scriptsWithStorage = docIdleUserscripts,
                runAt = UserscriptRunAt.DOCUMENT_IDLE,
                bridgeToken = tokens.gmToken,
                runtimeKey = tokens.gmKey
            ).forEach { snippet ->
                webView.evaluateJavascript(snippet, null)
            }
        }
    }

    private fun injectDarkReaderFastOrFull(
        webView: WebView,
        tokens: BridgeTokenSet,
        darkState: ResolvedWebDarkState,
        darkReaderInjector: DarkReaderInjector,
        isDestroyed: () -> Boolean
    ) {
        if (isDestroyed()) return
        if (!darkState.useDarkReader) {
            webView.evaluateJavascript(darkReaderInjector.buildDisableScript(tokens.darkReaderKey), null)
            return
        }
        val originAtCall = extractOrigin(webView.url.orEmpty()) ?: return
        val keyLit = json.encodeToString(tokens.darkReaderKey)
        webView.evaluateJavascript("Object.prototype.hasOwnProperty.call(window, $keyLit)") { loaded ->
            if (isDestroyed()) return@evaluateJavascript
            if (extractOrigin(webView.url.orEmpty()) != originAtCall) return@evaluateJavascript
            val script = if (loaded?.trim().equals("true", ignoreCase = true)) {
                darkReaderInjector.buildFastApplyScript(darkState, tokens.darkReaderKey)
            } else {
                darkReaderInjector.buildApplyScript(darkState, tokens.darkReaderToken, tokens.darkReaderKey)
            }
            webView.evaluateJavascript(script, null)
        }
    }
}
