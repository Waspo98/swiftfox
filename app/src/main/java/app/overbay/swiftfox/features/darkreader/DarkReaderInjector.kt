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

    val isSubframeRuntimeWarmed: Boolean
        get() = assetsWarmed

    fun warmUpAssets() {
        darkReaderJs
        amoledJs
        bridgeJs
        subframeJs
        subframeRuntimeScript
        assetsWarmed = true
    }

    /**
     * Static, token-free document-start runtime for cross-origin and same-origin iframes.
     * Exits immediately in `window === window.top` and lazily initializes `darkreader.min.js`
     * only inside visible, non-denylisted subframes.
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
        return buildString(darkReaderSource.length + amoledSource.length + subframeSource.length + 380) {
            append("(function(){\n")
            append("if(window===window.top)return;\n")
            append("var ensureDarkReader=function(){\n")
            append("if(typeof DarkReader==='undefined'||typeof DarkReader.enable!=='function'){\n")
            append("(function(define,exports,module){\n")
            append(darkReaderSource)
            append("\n}).call(globalThis,undefined,undefined,undefined);\n")
            append("}\n")
            append("};\n")
            append("var createAmoled=")
            append(amoledSource)
            append(";\n")
            append(subframeSource)
            append("(ensureDarkReader,createAmoled);\n")
            append("})();")
        }
    }

    /**
     * Full script that lazily injects `darkreader.min.js` + `darkreader_bridge.js` under an
     * unguessable per-session property key, binds the bridge token inside a closure, and applies [state].
     */
    fun buildApplyScript(
        state: ResolvedWebDarkState,
        bridgeToken: String = "",
        runtimeKey: String = DEFAULT_RUNTIME_KEY
    ): String {
        if (!state.enabled) {
            return buildDisableScript(runtimeKey)
        }
        val configJson = DarkReaderConfig.toConfigJson(state)
        val keyLit = json.encodeToString(runtimeKey)
        val tokenLit = json.encodeToString(bridgeToken)
        return buildString(darkReaderJs.length + amoledJs.length + bridgeJs.length + configJson.length + 380) {
            append("(function(){\n")
            if (darkReaderJs.isNotEmpty()) {
                append("if(typeof DarkReader==='undefined'||typeof DarkReader.enable!=='function'){\n")
                append("(function(define,exports,module){\n")
                append(darkReaderJs)
                append("\n}).call(globalThis,undefined,undefined,undefined);\n")
                append("}\n")
            }
            if (bridgeJs.isNotEmpty()) {
                append("if(!Object.prototype.hasOwnProperty.call(window,").append(keyLit).append(")){\n")
                append("var createAmoled=")
                if (amoledJs.isNotEmpty()) {
                    append(amoledJs)
                } else {
                    append("null")
                }
                append(";\n")
                append(bridgeJs)
                append("(").append(keyLit).append(",").append(tokenLit).append(",createAmoled);\n")
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
