package app.overbay.swiftfox.features.darkreader

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DarkReaderStatusEvent(
    val skippedNativeDark: Boolean
)

class DarkReaderInjector(private val context: Context?) {

    private val json = Json { ignoreUnknownKeys = true }

    private val darkReaderJs: String by lazy {
        readAsset("js/darkreader.min.js")
    }

    private val bridgeJs: String by lazy {
        readAsset("js/darkreader_bridge.js")
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
        if (!state.useDarkReader) {
            return buildDisableScript(runtimeKey)
        }
        val configJson = DarkReaderConfig.toConfigJson(state)
        val keyLit = json.encodeToString(runtimeKey)
        val tokenLit = json.encodeToString(bridgeToken)
        return buildString(darkReaderJs.length + bridgeJs.length + configJson.length + 320) {
            append("(function(){\n")
            if (darkReaderJs.isNotEmpty()) {
                append("if(typeof DarkReader==='undefined'){\n")
                append("(function(define,exports,module){\n")
                append(darkReaderJs)
                append("\n}).call(globalThis,undefined,undefined,undefined);\n")
                append("}\n")
            }
            if (bridgeJs.isNotEmpty()) {
                append("if(!Object.prototype.hasOwnProperty.call(window,").append(keyLit).append(")){\n")
                append(bridgeJs)
                append("(").append(keyLit).append(",").append(tokenLit).append(");\n")
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
     * Lightweight ~150-byte update script used when the Dark Reader runtime is already attached.
     */
    fun buildFastApplyScript(
        state: ResolvedWebDarkState,
        runtimeKey: String = DEFAULT_RUNTIME_KEY
    ): String {
        if (!state.useDarkReader) {
            return buildDisableScript(runtimeKey)
        }
        val configJson = DarkReaderConfig.toConfigJson(state)
        val keyLit = json.encodeToString(runtimeKey)
        return "(function(){var rt=window[$keyLit];if(rt&&typeof rt.apply==='function'){rt.apply($configJson);}})();"
    }

    fun buildDisableScript(runtimeKey: String = DEFAULT_RUNTIME_KEY): String {
        val keyLit = json.encodeToString(runtimeKey)
        return "(function(){var rt=window[$keyLit];if(rt&&typeof rt.apply==='function'){rt.apply({enabled:false});}else if(typeof DarkReader!=='undefined'){DarkReader.disable();}})();"
    }

    fun parseStatusJson(raw: String): DarkReaderStatusEvent? {
        if (raw.isBlank()) return null
        return runCatching {
            json.decodeFromString<DarkReaderStatusEvent>(raw)
        }.getOrNull()
    }

    private fun readAsset(assetPath: String): String {
        val ctx = context ?: return ""
        return runCatching {
            ctx.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
    }

    companion object {
        const val DEFAULT_RUNTIME_KEY = "__swiftfoxDarkReader"
    }
}
