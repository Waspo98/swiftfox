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

/**
 * Coordinates WebView script injection across `addDocumentStartJavaScript`, `onPageStarted`,
 * and `onPageFinished` for cosmetic rules, element zapper CSS, Dark Reader, and userscripts.
 */
object ScriptInjector {

    private val json = Json

    fun buildCssStyleScript(styleId: String, css: String): String {
        val idLit = json.encodeToString(styleId)
        val cssLit = json.encodeToString(css)
        return """
            (function() {
                if (location && location.protocol !== 'http:' && location.protocol !== 'https:') return;
                var id = $idLit;
                var css = $cssLit;
                var existing = document.getElementById(id);
                if (!css) {
                    if (existing) existing.textContent = '';
                    return;
                }
                if (!existing) {
                    existing = document.createElement('style');
                    existing.id = id;
                    existing.type = 'text/css';
                    var target = document.head || document.documentElement;
                    if (target) target.appendChild(existing);
                }
                existing.textContent = css;
            })();
        """.trimIndent()
    }

    fun registerDocumentStartScript(
        webView: WebView,
        previousHandler: ScriptHandler?,
        cosmeticCss: String,
        zappedCss: String,
        documentStartUserscriptJs: String
    ): ScriptHandler? {
        runCatching { previousHandler?.remove() }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return null
        }

        val combined = buildString {
            append("(function() {\n")
            append("if (!window.location || (window.location.protocol !== 'http:' && window.location.protocol !== 'https:')) return;\n")
            if (cosmeticCss.isNotBlank()) {
                append(buildCssStyleScript("swiftfox-cosmetic", cosmeticCss)).append('\n')
            }
            if (zappedCss.isNotBlank()) {
                append(buildCssStyleScript("swiftfox-zapped", zappedCss)).append('\n')
            }
            if (documentStartUserscriptJs.isNotBlank()) {
                append(documentStartUserscriptJs).append('\n')
            }
            append("})();")
        }

        if (cosmeticCss.isBlank() && zappedCss.isBlank() && documentStartUserscriptJs.isBlank()) {
            return null
        }

        return runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, combined, setOf("*"))
        }.getOrNull()
    }

    fun injectOnPageStarted(
        webView: WebView,
        cosmetic: CosmeticPayload,
        zappedRules: List<ZappedRuleEntity>,
        zapperController: ZapperController,
        darkState: ResolvedWebDarkState,
        darkReaderInjector: DarkReaderInjector,
        docStartUserscripts: List<Pair<UserscriptEntity, Map<String, String>>>,
        userscriptBridge: UserscriptBridge
    ) {
        if (cosmetic.css.isNotBlank()) {
            webView.evaluateJavascript(buildCssStyleScript("swiftfox-cosmetic", cosmetic.css), null)
        } else {
            webView.evaluateJavascript(buildCssStyleScript("swiftfox-cosmetic", ""), null)
        }
        if (cosmetic.injectedScript.isNotBlank()) {
            webView.evaluateJavascript(cosmetic.injectedScript, null)
        }

        webView.evaluateJavascript(zapperController.buildApplyZappedCssScript(zappedRules), null)

        if (docStartUserscripts.isNotEmpty()) {
            val js = userscriptBridge.buildInjectionScript(docStartUserscripts, UserscriptRunAt.DOCUMENT_START)
            if (js.isNotBlank()) {
                webView.evaluateJavascript(js, null)
            }
        }

        if (darkState.useDarkReader) {
            webView.evaluateJavascript(darkReaderInjector.buildApplyScript(darkState), null)
        } else {
            webView.evaluateJavascript(darkReaderInjector.buildDisableScript(), null)
        }
    }

    fun injectOnPageFinished(
        webView: WebView,
        cosmetic: CosmeticPayload,
        zappedRules: List<ZappedRuleEntity>,
        zapperController: ZapperController,
        darkState: ResolvedWebDarkState,
        darkReaderInjector: DarkReaderInjector,
        docEndUserscripts: List<Pair<UserscriptEntity, Map<String, String>>>,
        docIdleUserscripts: List<Pair<UserscriptEntity, Map<String, String>>>,
        userscriptBridge: UserscriptBridge
    ) {
        if (cosmetic.css.isNotBlank()) {
            webView.evaluateJavascript(buildCssStyleScript("swiftfox-cosmetic", cosmetic.css), null)
        }
        if (cosmetic.injectedScript.isNotBlank()) {
            webView.evaluateJavascript(cosmetic.injectedScript, null)
        }
        webView.evaluateJavascript(zapperController.buildApplyZappedCssScript(zappedRules), null)

        if (darkState.useDarkReader) {
            webView.evaluateJavascript(darkReaderInjector.buildApplyScript(darkState), null)
        } else {
            webView.evaluateJavascript(darkReaderInjector.buildDisableScript(), null)
        }

        if (docEndUserscripts.isNotEmpty()) {
            val endJs = userscriptBridge.buildInjectionScript(docEndUserscripts, UserscriptRunAt.DOCUMENT_END)
            if (endJs.isNotBlank()) {
                webView.evaluateJavascript(endJs, null)
            }
        }
        if (docIdleUserscripts.isNotEmpty()) {
            val idleJs = userscriptBridge.buildInjectionScript(docIdleUserscripts, UserscriptRunAt.DOCUMENT_IDLE)
            if (idleJs.isNotBlank()) {
                webView.evaluateJavascript(idleJs, null)
            }
        }
    }
}