package app.overbay.swiftfox.features.omnibox

import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.data.db.HistoryEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.features.sync.MozillaSyncCoordinator
import app.overbay.swiftfox.ui.browser.BrowserUiState
import java.util.Locale

/**
 * Pure helper that assembles prioritized [AutocompleteCandidate] lists and merges remote
 * navigational/search suggestions cleanly outside `BrowserViewModel`.
 */
object OmniboxCandidateBuilder {

    fun buildInMemoryCandidates(
        state: BrowserUiState,
        maxBookmarksAndHistory: Int = Int.MAX_VALUE
    ): List<AutocompleteCandidate> {
        val inPrivate = state.isPrivateView
        val isBoundedFastPass = maxBookmarksAndHistory < Int.MAX_VALUE
        val maxTabs = if (isBoundedFastPass) 40 else Int.MAX_VALUE
        val maxPins = if (isBoundedFastPass) 40 else Int.MAX_VALUE
        val maxRemoteTabs = if (isBoundedFastPass) 50 else Int.MAX_VALUE
        return buildList {
            var tabsAdded = 0
            for (tab in state.tabs) {
                if (tabsAdded >= maxTabs) break
                if (tab.isPrivate == inPrivate) {
                    val effectiveUrl = if (tab.isUnloaded) {
                        tab.pinnedUrl?.takeIf { it.isNotBlank() } ?: tab.url
                    } else {
                        tab.url
                    }
                    if (effectiveUrl.isNotBlank() && effectiveUrl != OmniboxEngine.NEW_TAB_URL) {
                        add(
                            AutocompleteCandidate(
                                url = effectiveUrl,
                                priority = if (tab.isUnloaded) {
                                    OmniboxEngine.PRIORITY_UNLOADED_TAB
                                } else {
                                    OmniboxEngine.PRIORITY_OPEN_TAB
                                },
                                visitCount = 1,
                                lastVisitedAt = tab.lastAccessedAt
                            )
                        )
                        tabsAdded++
                    }
                }
            }
            if (!inPrivate) {
                val essSlice = if (state.essentials.size > maxPins) state.essentials.take(maxPins) else state.essentials
                essSlice.forEach { ess ->
                    add(
                        AutocompleteCandidate(
                            url = ess.url,
                            priority = OmniboxEngine.PRIORITY_ESSENTIAL_OR_PIN,
                            visitCount = 2
                        )
                    )
                }
                val zenSlice = if (state.zenPinnedItems.size > maxPins) {
                    state.zenPinnedItems.take(maxPins)
                } else {
                    state.zenPinnedItems
                }
                zenSlice.forEach { pin ->
                    add(
                        AutocompleteCandidate(
                            url = pin.url,
                            priority = OmniboxEngine.PRIORITY_ESSENTIAL_OR_PIN,
                            visitCount = 2
                        )
                    )
                }
                val remoteSlice = if (state.syncedRemoteTabs.size > maxRemoteTabs) {
                    state.syncedRemoteTabs.take(maxRemoteTabs)
                } else {
                    state.syncedRemoteTabs
                }
                remoteSlice.forEach { remoteTab ->
                    add(
                        AutocompleteCandidate(
                            url = remoteTab.url,
                            priority = OmniboxEngine.PRIORITY_REMOTE_TAB,
                            visitCount = 2,
                            lastVisitedAt = remoteTab.lastUsedAt
                        )
                    )
                }
            }
            val bookmarkSlice = if (state.bookmarks.size > maxBookmarksAndHistory) {
                state.bookmarks.take(maxBookmarksAndHistory)
            } else {
                state.bookmarks
            }
            bookmarkSlice.forEach { bm ->
                add(
                    AutocompleteCandidate(
                        url = bm.url,
                        priority = OmniboxEngine.PRIORITY_BOOKMARK,
                        visitCount = 2,
                        lastVisitedAt = bm.updatedAt.coerceAtLeast(bm.createdAt)
                    )
                )
            }
            if (!inPrivate) {
                val historySlice = if (state.history.size > maxBookmarksAndHistory) {
                    state.history.take(maxBookmarksAndHistory)
                } else {
                    state.history
                }
                historySlice.forEach { h ->
                    add(
                        AutocompleteCandidate(
                            url = h.url,
                            priority = OmniboxEngine.PRIORITY_HISTORY,
                            visitCount = h.visitCount,
                            lastVisitedAt = h.lastVisitedAt
                        )
                    )
                }
            }
        }
    }

    fun fromBookmarks(bookmarks: List<BookmarkEntity>): List<AutocompleteCandidate> =
        bookmarks.map { bm ->
            AutocompleteCandidate(
                url = bm.url,
                priority = OmniboxEngine.PRIORITY_BOOKMARK,
                visitCount = 2,
                lastVisitedAt = bm.updatedAt.coerceAtLeast(bm.createdAt)
            )
        }

    fun fromHistory(history: List<HistoryEntity>): List<AutocompleteCandidate> =
        history.map { h ->
            AutocompleteCandidate(
                url = h.url,
                priority = OmniboxEngine.PRIORITY_HISTORY,
                visitCount = h.visitCount,
                lastVisitedAt = h.lastVisitedAt
            )
        }

    fun fromPlacesMatches(
        matches: List<MozillaSyncCoordinator.PlacesOmniboxMatch>
    ): List<AutocompleteCandidate> = matches.map { hit ->
        val syntheticVisits = when {
            hit.fromAutocomplete -> 4
            hit.score >= 500 -> 3
            hit.score >= 100 -> 2
            else -> 1
        }
        AutocompleteCandidate(
            url = hit.url,
            priority = OmniboxEngine.PRIORITY_HISTORY,
            visitCount = syntheticVisits,
            lastVisitedAt = 0L
        )
    }

    fun fromNavigationalSuggestions(
        hits: List<NavigationalSuggestion>
    ): List<AutocompleteCandidate> = hits.map { hit ->
        AutocompleteCandidate(
            url = hit.url,
            priority = OmniboxEngine.PRIORITY_NAV_SUGGESTION,
            visitCount = hit.relevance.coerceAtLeast(1),
            lastVisitedAt = 0L
        )
    }

    private fun isRootUrl(url: String): Boolean {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return false
        val pathAndRest = afterScheme.substringAfter('/', "")
        return pathAndRest.isEmpty() && !afterScheme.contains('?') && !afterScheme.contains('#')
    }

    fun mergeLiveSuggestions(
        existingSuggestions: List<OmniboxSuggestion>,
        liveResult: LiveSuggestionsResult,
        settings: BrowserSettings,
        allowNavPromotion: Boolean
    ): List<OmniboxSuggestion> {
        val merged = existingSuggestions.toMutableList()
        if (allowNavPromotion && liveResult.navigationalHits.isNotEmpty()) {
            val existingHostsForRoots = merged
                .filter {
                    it.kind == SuggestionKind.DIRECT_URL ||
                        ((it.kind == SuggestionKind.OPEN_TAB ||
                            it.kind == SuggestionKind.BOOKMARK ||
                            it.kind == SuggestionKind.HISTORY) && isRootUrl(it.targetUrl))
                }
                .map { OmniboxEngine.extractHost(it.targetUrl) }
                .filter { it.isNotBlank() }
                .toMutableSet()
            val existingNormalizedUrls = merged
                .map { it.targetUrl.trimEnd('/').lowercase(Locale.ROOT) }
                .toMutableSet()

            var insertAt = merged
                .indexOfFirst { it.kind != SuggestionKind.DIRECT_URL && it.kind != SuggestionKind.OPEN_TAB }
                .let { if (it < 0) merged.size else it }
            for (nav in liveResult.navigationalHits) {
                val normUrl = nav.url.trimEnd('/').lowercase(Locale.ROOT)
                val navHost = OmniboxEngine.extractHost(nav.url)
                val navIsRoot = isRootUrl(nav.url)
                if (normUrl in existingNormalizedUrls) continue
                if (navIsRoot && navHost.isNotBlank() && navHost in existingHostsForRoots) continue

                merged.add(
                    insertAt++,
                    OmniboxSuggestion(
                        id = "nav_${nav.url}",
                        title = nav.displayHost,
                        subtitle = if (nav.title.equals(nav.displayHost, ignoreCase = true)) {
                            "Go to suggested site"
                        } else {
                            "${nav.title} · Go to site"
                        },
                        targetUrl = nav.url,
                        kind = SuggestionKind.DIRECT_URL
                    )
                )
                existingNormalizedUrls += normUrl
                if (navIsRoot && navHost.isNotBlank()) existingHostsForRoots += navHost
            }
        }

        val engineDisplayName = settings.searchEngine.displayName(settings.customSearchUrlTemplate)
        liveResult.searchQueries.forEach { suggestionText ->
            val url = OmniboxEngine.buildSearchUrl(suggestionText, settings)
            if (merged.none { it.title.equals(suggestionText, ignoreCase = true) }) {
                merged += OmniboxSuggestion(
                    id = "live_$suggestionText",
                    title = suggestionText,
                    subtitle = engineDisplayName,
                    targetUrl = url,
                    kind = SuggestionKind.SEARCH
                )
            }
        }
        return merged
    }
}
