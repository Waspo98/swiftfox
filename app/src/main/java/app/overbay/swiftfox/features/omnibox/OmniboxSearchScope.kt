package app.overbay.swiftfox.features.omnibox

import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.SearchEngineId

/**
 * One-time search target chosen from the "This time search in:" picker on the Start Page or
 * focused Omnibox. Automatically resets when the omnibox is dismissed or submitted so the user's
 * saved default search engine in Settings is never overwritten.
 */
sealed interface OmniboxSearchScope {
    data class Engine(val engine: SearchEngineId) : OmniboxSearchScope
    data object Bookmarks : OmniboxSearchScope
    data object Tabs : OmniboxSearchScope
    data object History : OmniboxSearchScope

    val isLocalFilter: Boolean
        get() = this !is Engine

    val shortLabel: String
        get() = displayLabel()

    fun displayLabel(customSearchUrlTemplate: String = ""): String = when (this) {
        is Engine -> engine.displayName(customSearchUrlTemplate)
        Bookmarks -> "Bookmarks"
        Tabs -> "Tabs"
        History -> "History"
    }

    fun placeholder(customSearchUrlTemplate: String = ""): String = when (this) {
        is Engine -> "Search ${engine.displayName(customSearchUrlTemplate)}"
        Bookmarks -> "Search bookmarks"
        Tabs -> "Search open tabs"
        History -> "Search history"
    }

    fun effectiveSettings(settings: BrowserSettings): BrowserSettings = when (this) {
        is Engine -> settings.copy(searchEngine = engine)
        Bookmarks, Tabs, History -> settings
    }
}
