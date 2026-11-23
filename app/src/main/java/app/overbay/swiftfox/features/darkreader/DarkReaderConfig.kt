package app.overbay.swiftfox.features.darkreader

import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.WebDarkEngine
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ResolvedWebDarkState(
    val enabled: Boolean,
    val engine: WebDarkEngine,
    val isSiteOverride: Boolean,
    val amoled: Boolean,
    val brightness: Int,
    val contrast: Int,
    val sepia: Int,
    val skipNativeDark: Boolean
) {
    val useDarkReader: Boolean get() = enabled && engine == WebDarkEngine.DARK_READER
    val useAlgorithmicLite: Boolean get() = enabled && engine == WebDarkEngine.ALGORITHMIC_LITE
}

object DarkReaderConfig {

    fun resolve(
        settings: BrowserSettings,
        siteOverride: SiteOverrideEntity?,
        isChromeDark: Boolean
    ): ResolvedWebDarkState {
        val siteForced = siteOverride?.webDarkModeOverride
        val enabled = siteForced ?: when (settings.webDarkMode) {
            WebDarkModePreference.OFF -> false
            WebDarkModePreference.ALWAYS_ON -> true
            WebDarkModePreference.FOLLOW_APP -> isChromeDark
        }
        return ResolvedWebDarkState(
            enabled = enabled,
            engine = settings.webDarkEngine,
            isSiteOverride = siteForced != null,
            amoled = settings.darkReaderAmoled,
            brightness = settings.darkReaderBrightness,
            contrast = settings.darkReaderContrast,
            sepia = settings.darkReaderSepia,
            skipNativeDark = settings.darkReaderSkipNativeDark
        )
    }

    fun toConfigJson(state: ResolvedWebDarkState): String = buildJsonObject {
        put("enabled", state.useDarkReader)
        put("amoled", state.amoled)
        put("brightness", state.brightness.coerceIn(50, 150))
        put("contrast", state.contrast.coerceIn(50, 150))
        put("sepia", state.sepia.coerceIn(0, 100))
        put("skipNativeDark", state.skipNativeDark)
        put("forceOverride", state.isSiteOverride)
    }.toString()
}
