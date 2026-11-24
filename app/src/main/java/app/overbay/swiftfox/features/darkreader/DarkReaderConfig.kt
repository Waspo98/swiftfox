package app.overbay.swiftfox.features.darkreader

import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ResolvedWebDarkState(
    val enabled: Boolean,
    val isSiteOverride: Boolean,
    val amoled: Boolean,
    val brightness: Int,
    val contrast: Int,
    val sepia: Int,
    val skipNativeDark: Boolean
)

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
            isSiteOverride = siteForced != null,
            amoled = settings.darkReaderAmoled,
            brightness = settings.darkReaderBrightness,
            contrast = settings.darkReaderContrast,
            sepia = settings.darkReaderSepia,
            skipNativeDark = settings.darkReaderSkipNativeDark
        )
    }

    fun toConfigJson(state: ResolvedWebDarkState): String = buildJsonObject {
        put("enabled", state.enabled)
        put("amoled", state.amoled)
        put("brightness", state.brightness.coerceIn(50, 150))
        put("contrast", state.contrast.coerceIn(50, 150))
        put("sepia", state.sepia.coerceIn(0, 100))
        put("skipNativeDark", state.skipNativeDark)
        put("forceOverride", state.isSiteOverride)
    }.toString()
}
