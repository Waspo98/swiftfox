package app.overbay.swiftfox.features.darkreader

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DarkReaderStatusEvent(
    val active: Boolean,
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

    fun buildApplyScript(state: ResolvedWebDarkState): String {
        if (!state.useDarkReader) {
            return buildDisableScript()
        }
        val configJson = DarkReaderConfig.toConfigJson(state)
        return buildString(darkReaderJs.length + bridgeJs.length + configJson.length + 256) {
            append("(function(){\n")
            if (darkReaderJs.isNotEmpty()) {
                append("if(typeof DarkReader==='undefined'){\n")
                append("(function(define,exports,module){\n")
                append(darkReaderJs)
                append("\n}).call(globalThis,undefined,undefined,undefined);\n")
                append("}\n")
            }
            if (bridgeJs.isNotEmpty()) {
                append("if(typeof window.__swiftfoxDarkReader==='undefined'){\n")
                append(bridgeJs)
                append("\n}\n")
            }
            append("if(typeof window.__swiftfoxDarkReader!=='undefined'){\n")
            append("window.__swiftfoxDarkReader.apply(")
            append(configJson)
            append(");\n")
            append("}\n")
            append("})();")
        }
    }

    fun buildDisableScript(): String =
        "(function(){if(typeof window.__swiftfoxDarkReader!=='undefined'){window.__swiftfoxDarkReader.apply({enabled:false});}else if(typeof DarkReader!=='undefined'){DarkReader.disable();}})();"

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
}
