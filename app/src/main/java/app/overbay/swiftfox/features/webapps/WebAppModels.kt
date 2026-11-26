package app.overbay.swiftfox.features.webapps

import kotlinx.serialization.Serializable

enum class WebAppDisplayMode(val wireValue: String) {
    FULLSCREEN("fullscreen"),
    STANDALONE("standalone"),
    MINIMAL_UI("minimal-ui"),
    BROWSER("browser");

    val isAppLike: Boolean
        get() = this == FULLSCREEN || this == STANDALONE || this == MINIMAL_UI

    companion object {
        fun fromRaw(raw: String?): WebAppDisplayMode = when (raw?.trim()?.lowercase()) {
            "fullscreen" -> FULLSCREEN
            "standalone" -> STANDALONE
            "minimal-ui" -> MINIMAL_UI
            "browser" -> BROWSER
            else -> BROWSER
        }
    }
}

@Serializable
data class WebAppShortcutItem(
    val name: String,
    val url: String,
    val description: String = ""
)

data class ManifestIconCandidate(
    val src: String,
    val sizes: String = "",
    val type: String = "",
    val purpose: String = "any"
)

data class ParsedWebAppManifest(
    val manifestUrl: String?,
    val name: String,
    val shortName: String,
    val startUrl: String,
    val scopeUrl: String,
    val displayMode: WebAppDisplayMode,
    val themeColorHex: String? = null,
    val backgroundColorHex: String? = null,
    val icons: List<ManifestIconCandidate> = emptyList(),
    val bestIconUrl: String? = null,
    val bestIconMaskable: Boolean = false,
    val shortcuts: List<WebAppShortcutItem> = emptyList()
)

data class PageWebAppMetadata(
    val pageUrl: String,
    val pageTitle: String = "",
    val manifestUrl: String? = null,
    val inlineManifestJson: String? = null,
    val applicationName: String? = null,
    val themeColorRaw: String? = null,
    val appleMobileCapable: Boolean = false,
    val mobileCapable: Boolean = false,
    val touchIcons: List<ManifestIconCandidate> = emptyList(),
    val pageIcons: List<ManifestIconCandidate> = emptyList()
)

data class PageInstallCandidate(
    val pageUrl: String,
    val pageTitle: String,
    val suggestedName: String,
    val suggestedShortName: String,
    val startUrl: String,
    val scopeUrl: String,
    val manifestUrl: String? = null,
    val displayMode: WebAppDisplayMode = WebAppDisplayMode.BROWSER,
    val isStandaloneCapable: Boolean = false,
    val hasBeforeInstallPrompt: Boolean = false,
    val themeColorHex: String? = null,
    val backgroundColorHex: String? = null,
    val bestIconUrl: String? = null,
    val bestIconMaskable: Boolean = false,
    val shortcuts: List<WebAppShortcutItem> = emptyList(),
    val existingInstalledAppId: String? = null
)
