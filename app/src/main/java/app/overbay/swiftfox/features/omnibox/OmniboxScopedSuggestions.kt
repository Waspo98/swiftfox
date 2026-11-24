package app.overbay.swiftfox.features.omnibox

import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.data.db.HistoryEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.features.sync.MozillaSyncCoordinator
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState

/**
 * Pure/suspend helpers for building omnibox suggestions across one-time search scopes
 * (`Bookmarks`, `Tabs`, `History`, and `Engine`) as well as shared tab/bang mapping so
 * query resolution stays out of `BrowserViewModel` and easily unit-testable.
 */
object OmniboxScopedSuggestions {

    fun buildBangSuggestion(
        bang: ResolvedBang,
        rawTrimmed: String,
        settings: BrowserSettings
    ): OmniboxSuggestion {
        val target = if (bang.query.isNotBlank()) {
            OmniboxEngine.resolveInput(rawTrimmed, settings)
        } else {
            OmniboxEngine.bangHomepageUrl(bang.bang)
        }
        return OmniboxSuggestion(
            id = "bang_${bang.triggerUsed}",
            title = if (bang.query.isNotBlank()) {
                "Search ${bang.bang.name} for \"${bang.query}\""
            } else {
                "${bang.bang.name} (${bang.triggerUsed})"
            },
            subtitle = "Bang shortcut",
            targetUrl = target,
            kind = SuggestionKind.BANG
        )
    }

    fun buildEngineScopePrimarySuggestion(
        trimmed: String,
        settings: BrowserSettings,
        scope: OmniboxSearchScope.Engine
    ): OmniboxSuggestion {
        val bang = OmniboxEngine.detectBang(trimmed)
        if (bang != null) {
            return buildBangSuggestion(bang, trimmed, settings)
        }
        val targetUrl = OmniboxEngine.resolveInput(trimmed, settings, searchScope = scope)
        val engineName = scope.displayLabel(settings.customSearchUrlTemplate)
        return OmniboxSuggestion(
            id = "search_scope_${scope.engine.name}_$trimmed",
            title = trimmed,
            subtitle = "Search with $engineName",
            targetUrl = targetUrl,
            kind = SuggestionKind.SEARCH
        )
    }

    fun filterMatchingTabSuggestions(
        tabs: List<BrowserTabUi>,
        trimmedQuery: String,
        inPrivate: Boolean,
        limit: Int,
        sortByRecency: Boolean = false
    ): List<OmniboxSuggestion> {
        val filtered = tabs.filter { tab ->
            val effectiveUrl = effectiveTabUrl(tab)
            effectiveUrl.isNotBlank() &&
                effectiveUrl != OmniboxEngine.NEW_TAB_URL &&
                tab.isPrivate == inPrivate &&
                (trimmedQuery.isEmpty() ||
                    tab.displayTitle.contains(trimmedQuery, ignoreCase = true) ||
                    effectiveUrl.contains(trimmedQuery, ignoreCase = true))
        }
        val sorted = if (sortByRecency) {
            filtered.sortedWith(compareBy<BrowserTabUi> { it.isUnloaded }.thenByDescending { it.lastAccessedAt })
        } else {
            filtered.sortedBy { it.isUnloaded }
        }
        return sorted.take(limit).map { tab ->
            val effectiveUrl = effectiveTabUrl(tab)
            val hostLabel = OmniboxEngine.extractHost(effectiveUrl).ifBlank { effectiveUrl }
            OmniboxSuggestion(
                id = "tab_${tab.id}",
                title = tab.displayTitle,
                subtitle = if (tab.isUnloaded) {
                    "Open saved tab · $hostLabel"
                } else {
                    "Switch to open tab · $hostLabel"
                },
                targetUrl = effectiveUrl,
                kind = SuggestionKind.OPEN_TAB,
                tabId = tab.id
            )
        }
    }

    suspend fun buildLocalScopeSuggestions(
        scope: OmniboxSearchScope,
        query: String,
        state: BrowserUiState,
        searchBookmarksByPrefix: suspend (String, Int) -> List<BookmarkEntity>,
        searchBookmarks: suspend (String, Int) -> List<BookmarkEntity>,
        searchHistoryByPrefix: suspend (String, Int) -> List<HistoryEntity>,
        searchHistory: suspend (String, Int) -> List<HistoryEntity>,
        searchSyncedHistory: suspend (String, Int) -> List<MozillaSyncCoordinator.PlacesOmniboxMatch>
    ): List<OmniboxSuggestion> {
        val trimmed = query.trim()
        val inPrivate = state.isPrivateView || state.activeTab?.isPrivate == true
        return when (scope) {
            OmniboxSearchScope.Bookmarks -> {
                val matches = if (trimmed.isEmpty()) {
                    state.bookmarks.take(12)
                } else {
                    val hostPrefix = OmniboxEngine.extractHostPrefixForDbQuery(query)
                    val dbPrefix = if (hostPrefix != null) {
                        searchBookmarksByPrefix(hostPrefix, 16)
                    } else {
                        emptyList()
                    }
                    val dbSearch = searchBookmarks(trimmed, 20)
                    (dbPrefix + dbSearch).distinctBy { it.id }.take(20)
                }
                matches.map { bm ->
                    OmniboxSuggestion(
                        id = "bm_${bm.id}",
                        title = bm.title,
                        subtitle = OmniboxEngine.extractHost(bm.url).ifBlank { bm.url },
                        targetUrl = bm.url,
                        kind = SuggestionKind.BOOKMARK
                    )
                }
            }

            OmniboxSearchScope.Tabs -> filterMatchingTabSuggestions(
                tabs = state.tabs,
                trimmedQuery = trimmed,
                inPrivate = inPrivate,
                limit = 20,
                sortByRecency = true
            )

            OmniboxSearchScope.History -> {
                if (inPrivate) return emptyList()
                val results = mutableListOf<OmniboxSuggestion>()
                if (trimmed.isEmpty()) {
                    state.history.take(15).forEach { h ->
                        results += OmniboxSuggestion(
                            id = "hist_${h.url}",
                            title = h.title,
                            subtitle = OmniboxEngine.extractHost(h.url).ifBlank { h.url },
                            targetUrl = h.url,
                            kind = SuggestionKind.HISTORY
                        )
                    }
                } else {
                    val hostPrefix = OmniboxEngine.extractHostPrefixForDbQuery(query)
                    val dbPrefix = if (hostPrefix != null) {
                        searchHistoryByPrefix(hostPrefix, 20)
                    } else {
                        emptyList()
                    }
                    val dbSearch = searchHistory(trimmed, 20)
                    (dbPrefix + dbSearch).distinctBy { it.url }.take(16).forEach { h ->
                        results += OmniboxSuggestion(
                            id = "hist_${h.url}",
                            title = h.title,
                            subtitle = OmniboxEngine.extractHost(h.url).ifBlank { h.url },
                            targetUrl = h.url,
                            kind = SuggestionKind.HISTORY
                        )
                    }
                    if (state.settings.syncHistoryEnabled) {
                        val placesMatches = searchSyncedHistory(trimmed, 15)
                        placesMatches.forEach { hit ->
                            if (results.size < 20 &&
                                results.none { it.targetUrl.trimEnd('/').equals(hit.url.trimEnd('/'), ignoreCase = true) }
                            ) {
                                val host = OmniboxEngine.extractHost(hit.url).ifBlank { hit.url }
                                results += OmniboxSuggestion(
                                    id = "places_${hit.url}",
                                    title = hit.title.ifBlank { host },
                                    subtitle = host,
                                    targetUrl = hit.url,
                                    kind = SuggestionKind.HISTORY
                                )
                            }
                        }
                    }
                }
                results
            }

            is OmniboxSearchScope.Engine -> emptyList()
        }
    }

    private fun effectiveTabUrl(tab: BrowserTabUi): String =
        if (tab.isUnloaded) {
            tab.pinnedUrl?.takeIf { it.isNotBlank() } ?: tab.url
        } else {
            tab.url
        }
}
