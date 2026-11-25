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
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.userscripts.UserscriptBridge
import app.overbay.swiftfox.features.userscripts.UserscriptRunAt
import app.overbay.swiftfox.features.zapper.ZapperController
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.util.Locale

data class GlobalPrepaintDarkConfig(
    val amoled: Boolean = false,
    val brightness: Int = 100,
    val contrast: Int = 100,
    val sepia: Int = 0,
    val skipNativeDark: Boolean = true,
    val globalEnabled: Boolean = false,
    val excludedHosts: Set<String> = emptySet(),
    val includedHosts: Set<String> = emptySet()
)

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
                var done = false;
                var obs = null;
                function runOnce() {
                    if (done) return;
                    if (attach()) {
                        done = true;
                        if (obs) {
                            obs.disconnect();
                            obs = null;
                        }
                        document.removeEventListener('DOMContentLoaded', runOnce);
                    }
                }
                if (!attach()) {
                    if (typeof MutationObserver === 'function') {
                        obs = new MutationObserver(runOnce);
                        obs.observe(document, { childList: true, subtree: true });
                    }
                    document.addEventListener('DOMContentLoaded', runOnce, { once: true });
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
        val bg = if (amoled) "#000000" else "#181818"
        val fg = if (amoled) "#ffffff" else "#e8e8e8"
        return "html{background-color:$bg!important;color-scheme:dark!important;}" +
            "body,body :not(iframe):not(img):not(video):not(canvas):not(picture):not(svg){background-color:$bg!important;color:$fg!important;}"
    }

    /**
     * Builds a lightweight, token-free document-start script registered across `*` origins so
     * every brand-new navigation starts in the resolved dark surface before first paint while
     * honoring per-site light/dark overrides synchronously.
     */
    fun buildGlobalPrepaintDarkScript(config: GlobalPrepaintDarkConfig): String {
        val normalizedExcluded = config.excludedHosts
            .map { OmniboxEngine.normalizeHost(it) }
            .filter { it.isNotBlank() }
            .sorted()
        val normalizedIncluded = config.includedHosts
            .map { OmniboxEngine.normalizeHost(it) }
            .filter { it.isNotBlank() }
            .sorted()

        if (!config.globalEnabled && normalizedIncluded.isEmpty()) return ""

        val css = buildPrepaintDarkCss(amoled = config.amoled)

        val cssLit = json.encodeToString(css)
        val hostsLit = json.encodeToString(if (config.globalEnabled) normalizedExcluded else normalizedIncluded)
        val modeCheck = if (config.globalEnabled) {
            "if (list.indexOf(host) !== -1) return;"
        } else {
            "if (list.indexOf(host) === -1) return;"
        }
        val cleanupLogic = """
            var postDomTimer = function() { setTimeout(cleanup, 1500); };
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', postDomTimer, { once: true });
            } else {
                postDomTimer();
            }
            setTimeout(cleanup, 6000);
        """.trimIndent()

        val excludedLit = json.encodeToString(normalizedExcluded)
        val includedLit = json.encodeToString(normalizedIncluded)
        val baseSubframeCfgJson = buildJsonObject {
            put("amoled", config.amoled)
            put("brightness", config.brightness.coerceIn(50, 150))
            put("contrast", config.contrast.coerceIn(50, 150))
            put("sepia", config.sepia.coerceIn(0, 100))
            put("skipNativeDark", config.skipNativeDark)
        }.toString()
        val globalEnabledLit = if (config.globalEnabled) "true" else "false"
        val subframeBranch = """
            if (window !== window.top) {
                var topHost = '';
                try {
                    if (window.location && window.location.ancestorOrigins && window.location.ancestorOrigins.length > 0) {
                        var topOrigin = window.location.ancestorOrigins[window.location.ancestorOrigins.length - 1];
                        if (topOrigin && topOrigin !== 'null') {
                            topHost = (new URL(topOrigin).hostname || '').trim().toLowerCase().replace(/^\[|\]$/g, '').replace(/^www\./, '');
                        }
                    }
                } catch (e) {}
                if (!topHost) {
                    try {
                        if (document.referrer) {
                            topHost = (new URL(document.referrer).hostname || '').trim().toLowerCase().replace(/^\[|\]$/g, '').replace(/^www\./, '');
                        }
                    } catch (e) {}
                }
                var sfExcluded = $excludedLit;
                var sfIncluded = $includedLit;
                var sfIsIncluded = !!(topHost && sfIncluded.indexOf(topHost) !== -1);
                var sfIsExcluded = !!(topHost && sfExcluded.indexOf(topHost) !== -1);
                var sfCfg = $baseSubframeCfgJson;
                sfCfg.enabled = $globalEnabledLit ? !sfIsExcluded : sfIsIncluded;
                sfCfg.forceOverride = sfIsIncluded;
                if (typeof window.__sfDarkSubframeBoot === 'function') {
                    window.__sfDarkSubframeBoot(sfCfg);
                } else {
                    try {
                        Object.defineProperty(window, '__sfDarkSubframePending', {
                            value: sfCfg,
                            writable: true,
                            enumerable: false,
                            configurable: true
                        });
                    } catch (e) {}
                }
                return;
            }
        """.trimIndent()

        return """
            (function() {
                $subframeBranch
                if (!location || (location.protocol !== 'http:' && location.protocol !== 'https:')) return;
                var host = (location.hostname || '').toLowerCase().replace(/^\[|\]$/g, '').replace(/^www\./, '');
                var list = $hostsLit;
                $modeCheck
                var id = 'swiftfox-prepaint-dark';
                var css = $cssLit;
                var attached = false;
                var cleaned = false;
                var obs = null;
                function attach() {
                    if (cleaned) return true;
                    var existing = document.getElementById(id);
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
                function cleanup() {
                    if (cleaned) return;
                    cleaned = true;
                    if (obs) {
                        obs.disconnect();
                        obs = null;
                    }
                    try {
                        var el = document.getElementById(id);
                        if (el && el.parentNode) el.parentNode.removeChild(el);
                    } catch (e) {}
                }
                function runOnce() {
                    if (attached || cleaned) return;
                    if (attach()) {
                        attached = true;
                        if (obs) {
                            obs.disconnect();
                            obs = null;
                        }
                        document.removeEventListener('DOMContentLoaded', runOnce);
                    }
                }
                if (!attach()) {
                    if (typeof MutationObserver === 'function') {
                        obs = new MutationObserver(runOnce);
                        obs.observe(document, { childList: true, subtree: true });
                    }
                    document.addEventListener('DOMContentLoaded', runOnce, { once: true });
                } else {
                    attached = true;
                }
                $cleanupLogic
            })();
        """.trimIndent()
    }

    fun registerGlobalPrepaintDarkScript(
        webView: WebView,
        previousHandler: ScriptHandler?,
        script: String
    ): ScriptHandler? {
        runCatching { previousHandler?.remove() }
        if (script.isBlank() || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return null
        }
        return runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        }.getOrNull()
    }

    /**
     * Lightweight, 100% passive `document-start` DOM scroll & touch observer for main-frame
     * top-of-page (`scrollTop == 0`) pull-to-refresh. Announces `touchGateReady` on load,
     * `DOMContentLoaded`, and `pageshow` (bfcache restore), and reports (`channel: "touchGate"`)
     * whenever a touch starts inside a scrolled container (`scrollTop > 1`),
     * `overscroll-behavior-y: none`, a nested modal/sheet using `overscroll-behavior-y: contain`,
     * a restrictive `touch-action`, an active inner scroll, or a JS `preventDefault()` touch
     * handler — while allowing top-of-page pull at `scrollTop == 0` on full-viewport main
     * scrollers (such as Google Fi's `.SSPGKf`).
     */
    fun buildTouchScrollGateScript(): String = """
        (function() {
            if (window !== window.top) return;
            if (window.__sfTouchGateInstalled) return;
            window.__sfTouchGateInstalled = true;

            var touching = false;
            var blockedSentForTouch = false;

            function postChannel(payload) {
                try {
                    var ch = window.SwiftfoxNativeChannel;
                    if (ch && typeof ch.postMessage === 'function') {
                        ch.postMessage(JSON.stringify(payload));
                    }
                } catch (_e) {}
            }

            function postReady() {
                postChannel({ channel: 'touchGateReady' });
            }

            function postGate(blocked) {
                postChannel({
                    channel: 'touchGate',
                    blocked: !!blocked
                });
            }

            postReady();
            document.addEventListener('DOMContentLoaded', postReady, { once: true, passive: true });
            window.addEventListener('pageshow', postReady, { passive: true });

            function blocksVerticalPull(touchAction) {
                if (!touchAction || touchAction === 'auto' || touchAction === 'manipulation') {
                    return false;
                }
                var lower = touchAction.toLowerCase();
                if (lower.indexOf('pan-y') !== -1 || lower.indexOf('pan-down') !== -1) {
                    return false;
                }
                return true;
            }

            function inspectTarget(target) {
                try {
                    if (window.scrollY > 1) return true;
                    var se = document.scrollingElement;
                    if (se && se.scrollTop > 1) return true;

                    var viewportH = window.innerHeight ||
                        (document.documentElement ? document.documentElement.clientHeight : 0) ||
                        0;
                    var el = target && target.nodeType === 1
                        ? target
                        : (target && target.parentElement ? target.parentElement : null);
                    var steps = 0;
                    while (el && steps < 48) {
                        steps++;
                        var tag = el.tagName;
                        if (tag === 'CANVAS' || tag === 'IFRAME') {
                            return true;
                        }
                        if (tag === 'INPUT' && (el.type === 'range' || el.type === 'color')) {
                            return true;
                        }
                        var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
                        if (style) {
                            var obY = style.overscrollBehaviorY || style.overscrollBehavior || '';
                            if (obY === 'none') {
                                return true;
                            }
                            if (blocksVerticalPull(style.touchAction)) {
                                return true;
                            }
                            var ovY = style.overflowY;
                            if ((ovY === 'auto' || ovY === 'scroll' || ovY === 'overlay') &&
                                el.scrollHeight > el.clientHeight + 1 &&
                                el.scrollTop > 1) {
                                return true;
                            }
                            // Block pull-to-refresh on nested modals/sheets/textareas using
                            // overscroll-behavior-y: contain, while allowing top-of-page pull
                            // at scrollTop == 0 on full-viewport main scrollers (e.g. Google Fi).
                            if (obY === 'contain') {
                                var isFullViewportMainScroller =
                                    viewportH > 0 && el.clientHeight >= viewportH * 0.7;
                                if (!isFullViewportMainScroller) {
                                    return true;
                                }
                            }
                        }
                        var next = el.parentElement;
                        if (!next && el.getRootNode) {
                            var root = el.getRootNode();
                            if (root && root.host) {
                                next = root.host;
                            }
                        }
                        el = next;
                    }
                    var docEl = document.documentElement;
                    if (docEl && window.getComputedStyle) {
                        var rootStyle = window.getComputedStyle(docEl);
                        var rootObY = rootStyle.overscrollBehaviorY || rootStyle.overscrollBehavior || '';
                        if (rootObY === 'none') {
                            return true;
                        }
                    }
                } catch (_e) {}
                return false;
            }

            document.addEventListener('touchstart', function(e) {
                touching = true;
                var multiTouch = !!(e.touches && e.touches.length > 1);
                var blocked = multiTouch || inspectTarget(e.target);
                blockedSentForTouch = blocked;
                postGate(blocked);
            }, { capture: true, passive: true });

            document.addEventListener('touchstart', function(e) {
                if (touching && !blockedSentForTouch && e.defaultPrevented) {
                    blockedSentForTouch = true;
                    postGate(true);
                }
            }, { passive: true });

            document.addEventListener('touchmove', function(e) {
                if (touching && !blockedSentForTouch && e.defaultPrevented) {
                    blockedSentForTouch = true;
                    postGate(true);
                }
            }, { passive: true });

            document.addEventListener('scroll', function() {
                if (touching && !blockedSentForTouch) {
                    blockedSentForTouch = true;
                    postGate(true);
                }
            }, { capture: true, passive: true });

            var endTouch = function(e) {
                if (!e.touches || e.touches.length === 0) {
                    touching = false;
                    blockedSentForTouch = false;
                }
            };
            document.addEventListener('touchend', endTouch, { capture: true, passive: true });
            document.addEventListener('touchcancel', endTouch, { capture: true, passive: true });
        })();
    """.trimIndent()

    fun registerGlobalTouchScrollGateScript(webView: WebView): ScriptHandler? {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return null
        }
        return runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, buildTouchScrollGateScript(), setOf("*"))
        }.getOrNull()
    }

    fun registerDocumentStartScripts(
        webView: WebView,
        previousHandlers: List<ScriptHandler>,
        origin: String,
        cosmeticCss: String,
        zappedCss: String,
        gmShimInitJs: String,
        documentStartUserscriptSnippets: List<String>
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

    /**
     * Wraps [script] so it only runs when `window.location.origin === expectedOrigin`,
     * preventing rotated bridge tokens from leaking into a departing origin during navigation.
     */
    fun wrapOriginGuardedScript(expectedOrigin: String, script: String): String {
        if (expectedOrigin.isBlank() || script.isBlank()) return ""
        val originLit = json.encodeToString(expectedOrigin)
        return buildString(script.length + originLit.length + 96) {
            append("(function(){\n")
            append("if (!window.location || window.location.origin !== ").append(originLit).append(") return;\n")
            append(script)
            append("\n})();")
        }
    }

    /**
     * Immediately injects Dark Reader on the Main thread during `onPageStarted` without waiting for
     * Adblock/Room I/O or a redundant fast-probe round-trip. Guarded to [expectedOrigin] so an
     * early evaluation can never leak tokens into a departing origin.
     */
    fun injectDarkReaderOnPageStarted(
        webView: WebView,
        expectedOrigin: String,
        tokens: BridgeTokenSet,
        darkState: ResolvedWebDarkState,
        darkReaderInjector: DarkReaderInjector,
        isDestroyed: () -> Boolean = { false }
    ) {
        if (isDestroyed() || expectedOrigin.isBlank()) return
        if (!darkState.enabled) {
            webView.evaluateJavascript(darkReaderInjector.buildDisableScript(tokens.darkReaderKey), null)
            return
        }
        val fullScript = darkReaderInjector.buildApplyScript(
            state = darkState,
            bridgeToken = tokens.darkReaderToken,
            runtimeKey = tokens.darkReaderKey
        )
        val guarded = wrapOriginGuardedScript(expectedOrigin, fullScript)
        if (guarded.isNotEmpty()) {
            webView.evaluateJavascript(guarded, null)
        }
    }

    fun injectDarkReaderFastOrFull(
        webView: WebView,
        tokens: BridgeTokenSet,
        darkState: ResolvedWebDarkState,
        darkReaderInjector: DarkReaderInjector,
        isDestroyed: () -> Boolean = { false }
    ) {
        if (isDestroyed()) return
        if (!darkState.enabled) {
            webView.evaluateJavascript(darkReaderInjector.buildDisableScript(tokens.darkReaderKey), null)
            return
        }
        val originAtCall = extractOrigin(webView.url.orEmpty()) ?: return
        val fastScript = darkReaderInjector.buildFastApplyScript(darkState, tokens.darkReaderKey)
        webView.evaluateJavascript(fastScript) { result ->
            if (isDestroyed()) return@evaluateJavascript
            if (result?.trim() == "\"ok\"") return@evaluateJavascript
            if (extractOrigin(webView.url.orEmpty()) != originAtCall) return@evaluateJavascript
            val fullScript = darkReaderInjector.buildApplyScript(
                darkState,
                tokens.darkReaderToken,
                tokens.darkReaderKey
            )
            val guarded = wrapOriginGuardedScript(originAtCall, fullScript)
            if (guarded.isNotEmpty()) {
                webView.evaluateJavascript(guarded, null)
            }
        }
    }

    fun injectOnPageStarted(
        webView: WebView,
        tokens: BridgeTokenSet,
        cosmetic: CosmeticPayload,
        zappedRules: List<ZappedRuleEntity>,
        zapperController: ZapperController,
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
    }

    fun injectOnPageFinished(
        webView: WebView,
        tokens: BridgeTokenSet,
        cosmetic: CosmeticPayload,
        runGenericCosmeticScan: Boolean,
        zappedRules: List<ZappedRuleEntity>,
        zapperController: ZapperController,
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
}
