package app.overbay.swiftfox.data.prefs

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DAY("Day"),
    NIGHT("Night")
}

enum class NightSurfaceStyle(val label: String) {
    DARK("Dark"),
    AMOLED("AMOLED Black")
}

enum class AccentPreset(val label: String, val previewHex: Long) {
    FOX_AMBER("Fox Amber", 0xFFE86A17),
    MIDNIGHT_INDIGO("Midnight Indigo", 0xFF5B6CFA),
    FOREST("Forest", 0xFF2E8B57),
    MONOCHROME("Monochrome", 0xFF6B6A70)
}

enum class DevicePosture(val label: String) {
    FOLDED("Folded"),
    UNFOLDED("Unfolded")
}

enum class ToolbarPlacement(val label: String, val unfoldedOnly: Boolean = false) {
    TOP("Top"),
    BOTTOM("Bottom"),
    SPLIT("Split"),
    SIDE_RAIL("Side Rail", unfoldedOnly = true)
}

enum class SiteMode(val label: String) {
    MOBILE("Mobile"),
    DESKTOP("Desktop")
}

enum class SearchEngineId(
    val label: String,
    val searchUrlTemplate: String,
    val suggestUrlTemplate: String?
) {
    GOOGLE(
        label = "Google",
        searchUrlTemplate = "https://www.google.com/search?q=%s",
        suggestUrlTemplate = "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"
    ),
    DUCKDUCKGO(
        label = "DuckDuckGo",
        searchUrlTemplate = "https://duckduckgo.com/?q=%s",
        suggestUrlTemplate = "https://duckduckgo.com/ac/?q=%s&type=list"
    ),
    KAGI(
        label = "Kagi",
        searchUrlTemplate = "https://kagi.com/search?q=%s",
        suggestUrlTemplate = "https://kagi.com/api/autosuggest?q=%s"
    ),
    BRAVE(
        label = "Brave Search",
        searchUrlTemplate = "https://search.brave.com/search?q=%s",
        suggestUrlTemplate = "https://search.brave.com/api/suggest?q=%s"
    ),
    STARTPAGE(
        label = "Startpage",
        searchUrlTemplate = "https://www.startpage.com/do/dsearch?query=%s",
        suggestUrlTemplate = null
    ),
    CUSTOM(
        label = "Custom",
        searchUrlTemplate = "https://www.google.com/search?q=%s",
        suggestUrlTemplate = null
    )
}

enum class ExternalAppLinkPolicy(val label: String, val description: String) {
    ASK("Ask each time", "Prompt before opening native apps"),
    ALWAYS_IN_SWIFTFOX("Always in Swiftfox", "Keep links inside the browser"),
    NATIVE_APP("Open native apps", "Hand off supported links directly")
}

enum class WebDarkModePreference(val label: String) {
    OFF("Off"),
    FOLLOW_APP("Follow theme"),
    ALWAYS_ON("Always dark")
}

enum class WebDarkEngine(val label: String) {
    DARK_READER("Dark Reader"),
    ALGORITHMIC_LITE("Fast / Lite")
}

enum class ReaderFontFamily(val label: String) {
    SANS("Sans"),
    SERIF("Serif"),
    MONO("Mono")
}

enum class ReaderSurfaceMode(val label: String) {
    FOLLOW_APP("Follow app"),
    DAY("Day"),
    SEPIA("Sepia"),
    DARK("Dark"),
    AMOLED("AMOLED")
}

data class PostureProfile(
    val toolbarPlacement: ToolbarPlacement,
    val defaultSiteMode: SiteMode,
    val showSpacesSidebar: Boolean = false
)

data class BrowserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val nightSurfaceStyle: NightSurfaceStyle = NightSurfaceStyle.DARK,
    val accentPreset: AccentPreset = AccentPreset.FOX_AMBER,
    val useDynamicColor: Boolean = false,
    val webDarkMode: WebDarkModePreference = WebDarkModePreference.FOLLOW_APP,
    val webDarkEngine: WebDarkEngine = WebDarkEngine.DARK_READER,
    val darkReaderAmoled: Boolean = false,
    val darkReaderBrightness: Int = 100,
    val darkReaderContrast: Int = 100,
    val darkReaderSepia: Int = 0,
    val darkReaderSkipNativeDark: Boolean = true,
    val adblockEnabled: Boolean = true,
    val adblockCosmeticEnabled: Boolean = true,
    val userscriptsEnabled: Boolean = true,
    val readerFontFamily: ReaderFontFamily = ReaderFontFamily.SANS,
    val readerFontSizeSp: Int = 18,
    val readerLineHeight: Float = 1.6f,
    val readerSurfaceMode: ReaderSurfaceMode = ReaderSurfaceMode.FOLLOW_APP,
    val foldedProfile: PostureProfile = PostureProfile(
        toolbarPlacement = ToolbarPlacement.SPLIT,
        defaultSiteMode = SiteMode.MOBILE,
        showSpacesSidebar = false
    ),
    val unfoldedProfile: PostureProfile = PostureProfile(
        toolbarPlacement = ToolbarPlacement.TOP,
        defaultSiteMode = SiteMode.DESKTOP,
        showSpacesSidebar = true
    ),
    val searchEngine: SearchEngineId = SearchEngineId.GOOGLE,
    val customSearchUrlTemplate: String = "https://www.google.com/search?q=%s",
    val searchSuggestionsEnabled: Boolean = true,
    val httpsOnlyMode: Boolean = true,
    val externalAppLinkPolicy: ExternalAppLinkPolicy = ExternalAppLinkPolicy.ASK,
    val pullToRefreshEnabled: Boolean = true,
    val swipeUrlBarSwitchTabs: Boolean = true,
    val hideToolbarsOnScroll: Boolean = true,
    val confirmClosePinnedOnSecondTap: Boolean = true,
    val backgroundMediaEnabled: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val syncBookmarksEnabled: Boolean = true,
    val syncHistoryEnabled: Boolean = true,
    val syncTabsEnabled: Boolean = true,
    val syncZenSpacesEnabled: Boolean = true,
    val syncPeriodicIntervalMinutes: Int = 60,
    val syncLastTimestampMs: Long = 0L,
    val syncLocalHistorySeeded: Boolean = false,
    val syncZenSeparateEssentials: Boolean? = null,
    val hasSeededDefaults: Boolean = false
) {
    fun profileFor(posture: DevicePosture): PostureProfile = when (posture) {
        DevicePosture.FOLDED -> foldedProfile
        DevicePosture.UNFOLDED -> unfoldedProfile
    }

    fun resolvedSearchTemplate(): String {
        val candidate = customSearchUrlTemplate.trim()
        val isHttpOrHttps = candidate.startsWith("https://", ignoreCase = true) ||
            candidate.startsWith("http://", ignoreCase = true)
        return if (searchEngine == SearchEngineId.CUSTOM && isHttpOrHttps && candidate.contains("%s")) {
            candidate
        } else {
            searchEngine.searchUrlTemplate
        }
    }
}
