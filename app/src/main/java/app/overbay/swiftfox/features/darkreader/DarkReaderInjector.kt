package app.overbay.swiftfox.features.darkreader

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DarkReaderStatusEvent(
    val skippedNativeDark: Boolean
)

class DarkReaderInjector(context: Context?) {

    private val appContext: Context? = context?.applicationContext ?: context
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var assetsWarmed: Boolean = false

    private val darkReaderJs: String by lazy {
        readAsset("js/darkreader.min.js")
    }

    private val amoledJs: String by lazy {
        readAsset("js/darkreader_amoled.js")
    }

    private val bridgeJs: String by lazy {
        readAsset("js/darkreader_bridge.js")
    }

    private val subframeJs: String by lazy {
        readAsset("js/darkreader_subframe.js")
    }

    private val subframeRuntimeScript: String by lazy {
        assembleSubframeRuntimeScript(darkReaderJs, amoledJs, subframeJs)
    }

    /**
     * Pre-concatenated static main-frame bootstrap factory (`function(rk, bt){...}`) so
     * [buildApplyScript] never re-copies ~137 KB of static JS assets across separate builders.
     */
    private val mainFrameBootstrapFactory: String by lazy {
        assembleMainFrameBootstrapFactory(darkReaderJs, amoledJs, bridgeJs)
    }

    val isSubframeRuntimeWarmed: Boolean
        get() = assetsWarmed

    fun warmUpAssets() {
        darkReaderJs
        amoledJs
        bridgeJs
        subframeJs
        subframeRuntimeScript
        mainFrameBootstrapFactory
        assetsWarmed = true
    }

    /**
     * Static, token-free document-start runtime for cross-origin and same-origin iframes.
     * Exits immediately in `window === window.top` and lazily initializes `darkreader.min.js`
     * inside a CommonJS closure without leaking `window.DarkReader` or `window.chrome.runtime`.
     */
    fun buildSubframeRuntimeScript(): String = subframeRuntimeScript

    internal fun assembleSubframeRuntimeScript(
        darkReaderSource: String,
        amoledSource: String,
        subframeSource: String
    ): String {
        if (darkReaderSource.isEmpty() || amoledSource.isEmpty() || subframeSource.isEmpty()) {
            return ""
        }
        return buildString(darkReaderSource.length + amoledSource.length + subframeSource.length + 760) {
            append("(function(){\n")
            append("if(window===window.top)return;\n")
            append("var __drRef=null;\n")
            append("var ensureDarkReader=function(){\n")
            append("if(!__drRef||typeof __drRef.enable!=='function'){\n")
            append("var __drExp={},__drMod={exports:__drExp},__drChrome={runtime:{}};\n")
            append("var __hadCh=Object.prototype.hasOwnProperty.call(window,'chrome'),__prevCh=window.chrome;\n")
            append("try{\n")
            append("(function(define,exports,module,chrome){\n")
            append(darkReaderSource)
            append("\n}).call(globalThis,undefined,__drExp,__drMod,__drChrome);\n")
            append("}finally{\n")
            append("if(!__hadCh){try{delete window.chrome;}catch(e){}}else{try{window.chrome=__prevCh;}catch(e){}}\n")
            append("}\n")
            append("__drRef=(__drMod.exports&&typeof __drMod.exports.enable==='function')?__drMod.exports:(typeof DarkReader!=='undefined'?DarkReader:null);\n")
            append("}\n")
            append("return __drRef;\n")
            append("};\n")
            append("var createAmoled=")
            append(amoledSource)
            append(";\n")
            append(subframeSource)
            append("(ensureDarkReader,createAmoled);\n")
            append("})();")
        }
    }

    private fun assembleMainFrameBootstrapFactory(
        darkReaderSource: String,
        amoledSource: String,
        bridgeSource: String
    ): String {
        if (bridgeSource.isEmpty()) return ""
        return buildString(darkReaderSource.length + amoledSource.length + bridgeSource.length + 680) {
            append("function(rk,bt){\n")
            append("var __drRef=null;\n")
            if (darkReaderSource.isNotEmpty()) {
                append("var __drExp={},__drMod={exports:__drExp},__drChrome={runtime:{}};\n")
                append("var __hadCh=Object.prototype.hasOwnProperty.call(window,'chrome'),__prevCh=window.chrome;\n")
                append("try{\n")
                append("(function(define,exports,module,chrome){\n")
                append(darkReaderSource)
                append("\n}).call(globalThis,undefined,__drExp,__drMod,__drChrome);\n")
                append("}finally{\n")
                append("if(!__hadCh){try{delete window.chrome;}catch(e){}}else{try{window.chrome=__prevCh;}catch(e){}}\n")
                append("}\n")
                append("__drRef=(__drMod.exports&&typeof __drMod.exports.enable==='function')?__drMod.exports:(typeof DarkReader!=='undefined'?DarkReader:null);\n")
            } else {
                append("__drRef=typeof DarkReader!=='undefined'?DarkReader:null;\n")
            }
            append("var createAmoled=")
            if (amoledSource.isNotEmpty()) {
                append(amoledSource)
            } else {
                append("null")
            }
            append(";\n")
            append(bridgeSource)
            append("(rk,bt,createAmoled,__drRef);\n")
            append("}")
        }
    }

    /**
     * Full script that lazily injects `darkreader.min.js` + `darkreader_bridge.js` under an
     * unguessable per-session property key, binds the bridge token inside a closure, and applies [state].
     * When [expectedOrigin] is non-blank, emits the origin guard directly into the single buffer
     * so callers avoid allocating a second >130 KB copy via `wrapOriginGuardedScript`.
     */
    fun buildApplyScript(
        state: ResolvedWebDarkState,
        bridgeToken: String = "",
        runtimeKey: String = DEFAULT_RUNTIME_KEY,
        expectedOrigin: String = ""
    ): String {
        if (!state.enabled) {
            val disable = buildDisableScript(runtimeKey)
            if (expectedOrigin.isBlank()) return disable
            val originLit = json.encodeToString(expectedOrigin)
            return "(function(){if(!window.location||window.location.origin!==$originLit)return;$disable})();"
        }
        val configJson = DarkReaderConfig.toConfigJson(state)
        val keyLit = json.encodeToString(runtimeKey)
        val tokenLit = json.encodeToString(bridgeToken)
        val originLit = if (expectedOrigin.isNotBlank()) json.encodeToString(expectedOrigin) else ""
        val factory = mainFrameBootstrapFactory
        return buildString(factory.length + configJson.length + originLit.length + 280) {
            append("(function(){\n")
            if (originLit.isNotEmpty()) {
                append("if(!window.location||window.location.origin!==").append(originLit).append(")return;\n")
            }
            if (factory.isNotEmpty()) {
                append("if(!Object.prototype.hasOwnProperty.call(window,").append(keyLit).append(")){\n")
                append("(").append(factory).append(")(").append(keyLit).append(",").append(tokenLit).append(");\n")
                append("}\n")
            }
            append("var rt=window[").append(keyLit).append("];\n")
            append("if(rt&&typeof rt.apply==='function'){\n")
            append("rt.apply(")
            append(configJson)
            append(");\n")
            append("}\n")
            append("})();")
        }
    }

    /**
     * Lightweight ~200-byte update script used when the Dark Reader runtime is already attached.
     * Returns `"ok"` when applied synchronously on the live window, or `"missing"` if the runtime
     * needs full injection (eliminating probe-to-apply races across same-origin navigations).
     */
    fun buildFastApplyScript(
        state: ResolvedWebDarkState,
        runtimeKey: String = DEFAULT_RUNTIME_KEY
    ): String {
        if (!state.enabled) {
            return buildDisableScript(runtimeKey)
        }
        val configJson = DarkReaderConfig.toConfigJson(state)
        val keyLit = json.encodeToString(runtimeKey)
        return "(function(){var rt=window[$keyLit];if(rt&&typeof rt.apply==='function'){rt.apply($configJson);return 'ok';}return 'missing';})();"
    }

    fun buildDisableScript(runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = json.encodeToString(runtimeKey)
        return "(function(){try{var p=document.getElementById('swiftfox-prepaint-dark');if(p&&p.parentNode){p.parentNode.removeChild(p);}}catch(e){}var rt=window[$keyLit];if(rt&&typeof rt.apply==='function'){rt.apply({enabled:false});}else{if(typeof DarkReader!=='undefined'&&typeof DarkReader.disable==='function'){DarkReader.disable();}try{var fs=document.querySelectorAll('iframe');for(var i=0;i<fs.length;i++){if(fs[i].contentWindow){fs[i].contentWindow.postMessage({__swiftfoxDarkSync:true,config:{enabled:false}},'*');}}}catch(e){}}})();"
    }

    fun parseStatusJson(raw: String): DarkReaderStatusEvent? {
        if (raw.isBlank()) return null
        return runCatching {
            json.decodeFromString<DarkReaderStatusEvent>(raw)
        }.getOrNull()
    }

    private fun readAsset(assetPath: String): String {
        val ctx = appContext ?: return ""
        return runCatching {
            ctx.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
    }

    companion object {
        const val DEFAULT_RUNTIME_KEY = "__swiftfoxDarkReader"
    }
}
